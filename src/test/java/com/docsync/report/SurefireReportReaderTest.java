package com.docsync.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.docsync.model.TestResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behaviour tests for {@link SurefireReportReader} aggregation and degradation
 * (FR-8, FR-9).
 */
class SurefireReportReaderTest {

    private final SurefireReportReader reader = new SurefireReportReader();

    @TempDir
    Path projectRoot;

    private void writeReport(String fileName, String content) throws IOException {
        Path dir = projectRoot.resolve(SurefireReportReader.REPORTS_DIR);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(fileName), content);
    }

    @Test
    void aggregatesCountsAcrossMultipleReports() throws IOException {
        writeReport("TEST-a.xml",
                "<testsuite name=\"a\" tests=\"4\" failures=\"1\" errors=\"0\" skipped=\"1\"/>");
        writeReport("TEST-b.xml",
                "<testsuite name=\"b\" tests=\"6\" failures=\"0\" errors=\"1\" skipped=\"0\"/>");

        TestResult result = reader.read(projectRoot).orElseThrow();

        assertEquals(10, result.total());
        assertEquals(7, result.passed());
        assertEquals(2, result.failed());
        assertEquals(1, result.skipped());
        assertEquals("FAIL", result.status());
    }

    @Test
    void reportsPassWhenAllTestsSucceed() throws IOException {
        writeReport("TEST-ok.xml",
                "<testsuite name=\"ok\" tests=\"3\" failures=\"0\" errors=\"0\" skipped=\"0\"/>");

        TestResult result = reader.read(projectRoot).orElseThrow();

        assertEquals("PASS", result.status());
        assertEquals(3, result.passed());
    }

    @Test
    void returnsEmptyWhenReportsDirectoryMissing() {
        assertTrue(reader.read(projectRoot).isEmpty());
    }

    @Test
    void returnsEmptyWhenNoReportIsValidXml() throws IOException {
        writeReport("TEST-broken.xml", "<testsuite tests=\"1\"");
        writeReport("TEST-other-root.xml", "<coverage/>");

        assertTrue(reader.read(projectRoot).isEmpty());
    }

    @Test
    void skipsInvalidReportButKeepsValidOnes() throws IOException {
        writeReport("TEST-broken.xml", "not xml at all");
        writeReport("TEST-good.xml",
                "<testsuite name=\"g\" tests=\"2\" failures=\"0\" errors=\"0\" skipped=\"0\"/>");

        Optional<TestResult> result = reader.read(projectRoot);

        assertEquals(2, result.orElseThrow().total());
    }

    @Test
    void returnsEmptyWhenCountersAreNegativeOrInconsistent() throws IOException {
        writeReport("TEST-negative.xml",
                "<testsuite tests=\"2\" failures=\"-1\" errors=\"0\" skipped=\"0\"/>");
        writeReport("TEST-inconsistent.xml",
                "<testsuite tests=\"2\" failures=\"1\" errors=\"0\" skipped=\"2\"/>");

        assertTrue(reader.read(projectRoot).isEmpty());
    }

    @Test
    void ignoresReportsNotModifiedAfterTheGivenTime() throws IOException {
        writeReport("TEST-stale.xml",
                "<testsuite tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"/>");

        assertTrue(reader.read(projectRoot, Instant.now().plusSeconds(1)).isEmpty());
    }
}
