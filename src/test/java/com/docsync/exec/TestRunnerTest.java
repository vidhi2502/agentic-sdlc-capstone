package com.docsync.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.docsync.report.JacocoReportReader;
import com.docsync.report.SurefireReportReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behaviour tests for Maven test execution and report aggregation.
 */
class TestRunnerTest {

    @TempDir
    Path projectRoot;

    private TestRunner runner(ProcessRunner processRunner) {
        return new TestRunner(processRunner, new SurefireReportReader(), new JacocoReportReader());
    }

    private void writeReports() throws IOException {
        Path surefireReport = projectRoot.resolve("target/surefire-reports/TEST-example.xml");
        Files.createDirectories(surefireReport.getParent());
        Files.writeString(surefireReport,
                "<testsuite tests=\"4\" failures=\"1\" errors=\"0\" skipped=\"1\"/>");
        Path jacocoReport = projectRoot.resolve("target/site/jacoco/jacoco.xml");
        Files.createDirectories(jacocoReport.getParent());
        Files.writeString(jacocoReport,
                "<report><counter type=\"LINE\" missed=\"1\" covered=\"3\"/></report>");
    }

    @Test
    void runsMavenAndCombinesSurefireAndJacocoReports() throws IOException {
        writeReports();
        FakeProcessRunner process = FakeProcessRunner.returning(new ProcessResult(0, "", false));

        TestRunResult result = runner(process).run(projectRoot, false);

        assertEquals(1, process.commands().size());
        assertEquals(List.of("mvn", "test"), process.commands().get(0));
        assertEquals(projectRoot, process.workingDirectories().get(0));
        assertEquals(Duration.ofMinutes(5), process.timeouts().get(0));
        assertEquals(4, result.testResult().orElseThrow().total());
        assertEquals(2, result.testResult().orElseThrow().passed());
        assertEquals(1, result.testResult().orElseThrow().failed());
        assertEquals(1, result.testResult().orElseThrow().skipped());
        assertEquals(75.0, result.testResult().orElseThrow().lineCoveragePercent(), 0.001);
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void doesNotLaunchMavenWhenTestsAreSkipped() {
        FakeProcessRunner process = FakeProcessRunner.returning(new ProcessResult(0, "", false));

        TestRunResult result = runner(process).run(projectRoot, true);

        assertTrue(result.testResult().isEmpty());
        assertTrue(result.warnings().isEmpty());
        assertTrue(process.commands().isEmpty());
    }

    @Test
    void reportsTestCountsWithNotFoundCoverageWhenJacocoReportIsMissing() throws IOException {
        Path report = projectRoot.resolve("target/surefire-reports/TEST-example.xml");
        Files.createDirectories(report.getParent());
        Files.writeString(report, "<testsuite tests=\"2\" failures=\"0\" errors=\"0\" skipped=\"0\"/>");
        FakeProcessRunner process = FakeProcessRunner.returning(new ProcessResult(0, "", false));

        TestRunResult result = runner(process).run(projectRoot, false);

        assertTrue(result.warnings().isEmpty());
        assertEquals(2, result.testResult().orElseThrow().total());
        assertTrue(result.testResult().orElseThrow().lineCoveragePercent() == null);
    }

    @Test
    void returnsNotFoundAndWarningWhenMavenCannotStart() {
        TestRunResult result = runner(FakeProcessRunner.failingToLaunch("mvn not found"))
                .run(projectRoot, false);

        assertTrue(result.testResult().isEmpty());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void returnsNotFoundAndWarningWhenMavenTimesOut() {
        FakeProcessRunner process = FakeProcessRunner.returning(new ProcessResult(-1, "", true));

        TestRunResult result = runner(process).run(projectRoot, false);

        assertTrue(result.testResult().isEmpty());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void returnsNotFoundAndWarningWhenSurefireReportIsMissing() {
        FakeProcessRunner process = FakeProcessRunner.returning(new ProcessResult(0, "", false));

        TestRunResult result = runner(process).run(projectRoot, false);

        assertTrue(result.testResult().isEmpty());
        assertFalse(result.warnings().isEmpty());
    }
}
