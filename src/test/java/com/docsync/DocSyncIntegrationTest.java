package com.docsync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.docsync.exec.ProcessResult;
import com.docsync.exec.ProcessRunner;
import com.docsync.exec.TestRunner;
import com.docsync.output.OutputWriter;
import com.docsync.output.ScriptedUserPrompt;
import com.docsync.render.MarkdownRenderer;
import com.docsync.report.JacocoReportReader;
import com.docsync.report.SurefireReportReader;
import com.docsync.scan.EntryPointScanner;
import com.docsync.scan.PomParser;
import com.docsync.scan.ScanOrchestrator;
import com.docsync.security.SecretGuard;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end documentation generation tests using temporary project folders.
 */
class DocSyncIntegrationTest {

    private static final List<String> REQUIRED_SECTIONS = List.of(
            "## Project Name",
            "## Description",
            "## Tech Stack",
            "## Dependencies",
            "## Entry Points",
            "## Test Status");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "(?i)\\bTODO\\b|lorem ipsum");

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesCompleteDocumentationForMavenProject() throws IOException {
        Path project = Files.createDirectory(temporaryDirectory.resolve("normal-maven"));
        writeNormalProject(project);
        List<List<String>> commands = new ArrayList<>();
        ProcessRunner maven = (command, workingDirectory, timeout) -> {
            commands.add(List.copyOf(command));
            writeFreshReports(workingDirectory);
            return new ProcessResult(0, "", false);
        };

        RunResult run = runCli(project, new String[] {project.toString()}, maven);
        String documentation = readDocumentation(project);

        assertEquals(0, run.exitCode());
        assertEquals(1, commands.size());
        assertDocumentationQuality(documentation, null);
        assertTrue(documentation.contains("Sample Application"));
        assertTrue(documentation.contains("A sample Maven project."));
        assertTrue(documentation.contains("- Java: 17"));
        assertTrue(documentation.contains("- Build tool: Maven"));
        assertTrue(documentation.contains("org.example:sample-lib"));
        assertTrue(documentation.contains("1.2.3"));
        assertTrue(documentation.contains("(compile)"));
        assertTrue(documentation.contains("`example.Main`"));
        assertTrue(documentation.contains("- Result: PASS"));
        assertTrue(documentation.contains("total 2, passed 2, failed 0, skipped 0"));
        assertTrue(documentation.contains("- Line coverage: 75.0%"));
    }

    @Test
    void generatesNotFoundDocumentationForEmptyFolder() throws IOException {
        Path project = Files.createDirectory(temporaryDirectory.resolve("empty"));

        RunResult run = runCli(project, new String[] {project.toString(), "--skip-tests"},
                (command, workingDirectory, timeout) -> {
                    throw new AssertionError("Maven should not run when tests are skipped");
                });
        String documentation = readDocumentation(project);

        assertEquals(1, run.exitCode());
        assertDocumentationQuality(documentation, null);
        assertMissingInformationIsNotFound(documentation);
    }

    @Test
    void excludesFakeEnvSecretFromGeneratedDocumentation() throws IOException {
        Path project = Files.createDirectory(temporaryDirectory.resolve("with-env"));
        String fakeSecret = "FAKE_TEST_SECRET_91ab27d3";
        Files.writeString(project.resolve(".env"), "API_TOKEN=" + fakeSecret);

        RunResult run = runCli(project, new String[] {project.toString(), "--skip-tests"},
                (command, workingDirectory, timeout) -> {
                    throw new AssertionError("Maven should not run when tests are skipped");
                });
        String documentation = readDocumentation(project);

        assertEquals(1, run.exitCode());
        assertDocumentationQuality(documentation, fakeSecret);
        assertMissingInformationIsNotFound(documentation);
        assertFalse(documentation.contains(".env"));
        assertFalse(run.stderr().contains(fakeSecret));
    }

    private void writeNormalProject(Path project) throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>sample-app</artifactId>
                  <name>Sample Application</name>
                  <description>A sample Maven project.</description>
                  <properties>
                    <maven.compiler.source>17</maven.compiler.source>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.example</groupId>
                      <artifactId>sample-lib</artifactId>
                      <version>1.2.3</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        Files.writeString(project.resolve("README.md"), "# Sample Application\nIntegration fixture.\n");
        Path main = project.resolve("src/main/java/example/Main.java");
        Files.createDirectories(main.getParent());
        Files.writeString(main, """
                package example;
                public class Main {
                    public static void main(String[] args) {}
                }
                """);
    }

    private void writeFreshReports(Path project) throws IOException {
        Path surefireReport = project.resolve("target/surefire-reports/TEST-example.xml");
        Files.createDirectories(surefireReport.getParent());
        Files.writeString(surefireReport,
                "<testsuite tests=\"2\" failures=\"0\" errors=\"0\" skipped=\"0\"/>");
        Files.setLastModifiedTime(surefireReport, FileTime.from(Instant.now().plusSeconds(1)));

        Path jacocoReport = project.resolve("target/site/jacoco/jacoco.xml");
        Files.createDirectories(jacocoReport.getParent());
        Files.writeString(jacocoReport,
                "<report><counter type=\"LINE\" missed=\"1\" covered=\"3\"/></report>");
        Files.setLastModifiedTime(jacocoReport, FileTime.from(Instant.now().plusSeconds(1)));
    }

    private RunResult runCli(Path project, String[] arguments, ProcessRunner processRunner)
            throws IOException {
        SecretGuard secretGuard = new SecretGuard();
        ScriptedUserPrompt prompt = new ScriptedUserPrompt(List.of());
        TestRunner testRunner = new TestRunner(
                processRunner, new SurefireReportReader(), new JacocoReportReader());
        ScanOrchestrator orchestrator = new ScanOrchestrator(
                new PomParser(), new EntryPointScanner(secretGuard), testRunner, secretGuard);
        DocSyncCli cli = new DocSyncCli(
                orchestrator,
                new MarkdownRenderer(secretGuard),
                new OutputWriter(prompt),
                prompt,
                secretGuard);
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode;
        try (PrintStream errorStream = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            exitCode = cli.run(arguments, project.getParent(), errorStream, false);
        }
        return new RunResult(exitCode, stderr.toString(StandardCharsets.UTF_8));
    }

    private String readDocumentation(Path project) throws IOException {
        Path output = project.resolve(OutputWriter.DOCUMENTATION_FILE_NAME);
        assertTrue(Files.isRegularFile(output), "DOCUMENTATION.md should be created");
        return Files.readString(output);
    }

    private void assertDocumentationQuality(String documentation, String fakeSecret) {
        for (String section : REQUIRED_SECTIONS) {
            assertTrue(documentation.contains(section), "Missing section: " + section);
        }
        assertEquals(6, documentation.lines().filter(line -> line.startsWith("## ")).count());
        assertFalse(PLACEHOLDER_PATTERN.matcher(documentation).find(),
                "Documentation must not contain TODO or lorem ipsum placeholders");
        if (fakeSecret != null) {
            assertFalse(documentation.contains(fakeSecret),
                    "The .env fixture secret must not appear in generated documentation");
        }
    }

    private void assertMissingInformationIsNotFound(String documentation) {
        assertTrue(documentation.contains("## Project Name\nNot Found"));
        assertTrue(documentation.contains("## Description\nNot Found"));
        assertTrue(documentation.contains("- Java: Not Found"));
        assertTrue(documentation.contains("- Build tool: Not Found"));
        assertTrue(documentation.contains("## Dependencies\nNot Found"));
        assertTrue(documentation.contains("## Entry Points\nNot Found"));
        assertTrue(documentation.contains("- Result: Not Found"));
        assertTrue(documentation.contains("- Tests: Not Found"));
        assertTrue(documentation.contains("- Line coverage: Not Found"));
    }

    private record RunResult(int exitCode, String stderr) {
    }
}
