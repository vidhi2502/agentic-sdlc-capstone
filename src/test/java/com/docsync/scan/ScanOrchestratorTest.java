package com.docsync.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.docsync.exec.FakeProcessRunner;
import com.docsync.exec.ProcessResult;
import com.docsync.exec.TestRunResult;
import com.docsync.exec.TestRunner;
import com.docsync.model.Dependency;
import com.docsync.model.TestResult;
import com.docsync.report.JacocoReportReader;
import com.docsync.report.SurefireReportReader;
import com.docsync.security.SecretGuard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behaviour tests for collection sequencing and independent failure handling.
 */
class ScanOrchestratorTest {

    @TempDir
    Path projectRoot;

    @Test
    void collectsProjectMetadataEntryPointsAndTestResults() throws IOException {
        Files.writeString(projectRoot.resolve("pom.xml"), """
                <project>
                  <artifactId>demo</artifactId>
                  <name>Demo Project</name>
                  <description>Example</description>
                  <properties><maven.compiler.source>17</maven.compiler.source></properties>
                  <dependencies><dependency><groupId>g</groupId><artifactId>a</artifactId>
                    <version>1.0</version></dependency></dependencies>
                </project>
                """);
        Path sourceFile = projectRoot.resolve("src/main/java/example/Main.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, "package example; public class Main { public static void main(String[] args) {} }");
        writeReport("target/surefire-reports/TEST-example.xml",
                "<testsuite tests=\"2\" failures=\"0\" errors=\"0\" skipped=\"0\"/>");
        writeReport("target/site/jacoco/jacoco.xml",
                "<report><counter type=\"LINE\" missed=\"1\" covered=\"3\"/></report>");

        ScanResult result = orchestrator().scan(projectRoot, false);

        assertEquals("Demo Project", result.projectInfo().name());
        assertEquals("17", result.projectInfo().javaVersion());
        assertEquals(List.of(new Dependency("g:a", "1.0", "compile")),
                result.projectInfo().dependencies());
        assertEquals(List.of("example.Main"), result.projectInfo().entryPoints());
        assertEquals(new TestResult(2, 2, 0, 0, 75.0),
                result.projectInfo().testResultOptional().orElseThrow());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void returnsNotFoundFieldsAndWarningsForMissingInputs() {
        ScanResult result = orchestrator().scan(projectRoot, false);

        assertNull(result.projectInfo().name());
        assertNull(result.projectInfo().buildTool());
        assertTrue(result.projectInfo().dependencies().isEmpty());
        assertTrue(result.projectInfo().entryPoints().isEmpty());
        assertTrue(result.projectInfo().testResultOptional().isEmpty());
        assertEquals(3, result.warnings().size());
    }

    @Test
    void isolatesExceptionsFromEveryCollectorAndDoesNotExposeExceptionText() {
        ScanOrchestrator orchestrator = new ScanOrchestrator(
                path -> {
                    throw new IllegalStateException("token=private-value");
                },
                path -> {
                    throw new IllegalStateException("source failure");
                },
                (path, skip) -> {
                    throw new IllegalStateException("test failure");
                },
                new SecretGuard());

        ScanResult result = orchestrator.scan(projectRoot, false);

        assertNull(result.projectInfo().name());
        assertTrue(result.projectInfo().entryPoints().isEmpty());
        assertTrue(result.projectInfo().testResultOptional().isEmpty());
        assertEquals(3, result.warnings().size());
        assertFalse(String.join(" ", result.warnings()).contains("private-value"));
        assertTrue(result.warnings().stream().allMatch(warning -> warning.startsWith("Unable to")));
    }

    @Test
    void preservesSuccessfulCollectorsWhenOneCollectorFails() {
        ScanOrchestrator orchestrator = new ScanOrchestrator(
                path -> {
                    throw new IllegalStateException("pom failure");
                },
                path -> new EntryPointResult(List.of("example.Main"), List.of()),
                (path, skip) -> new TestRunResult(
                        Optional.of(new TestResult(1, 1, 0, 0, null)), List.of()),
                new SecretGuard());

        ScanResult result = orchestrator.scan(projectRoot, true);

        assertNull(result.projectInfo().name());
        assertEquals(List.of("example.Main"), result.projectInfo().entryPoints());
        assertEquals(1, result.projectInfo().testResultOptional().orElseThrow().total());
        assertEquals(1, result.warnings().size());
    }

    private ScanOrchestrator orchestrator() {
        TestRunner testRunner = new TestRunner(
                FakeProcessRunner.returning(new ProcessResult(0, "", false)),
                new SurefireReportReader(),
                new JacocoReportReader());
        return new ScanOrchestrator(
                new PomParser(),
                new EntryPointScanner(new SecretGuard()),
                testRunner,
                new SecretGuard());
    }

    private void writeReport(String relativePath, String content) throws IOException {
        Path report = projectRoot.resolve(relativePath);
        Files.createDirectories(report.getParent());
        Files.writeString(report, content);
    }
}
