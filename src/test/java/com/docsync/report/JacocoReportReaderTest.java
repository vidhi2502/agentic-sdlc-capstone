package com.docsync.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behaviour tests for {@link JacocoReportReader} coverage extraction and
 * degradation (FR-8, FR-9, DR-9).
 */
class JacocoReportReaderTest {

    private final JacocoReportReader reader = new JacocoReportReader();

    @TempDir
    Path projectRoot;

    private void writeReport(String content) throws IOException {
        Path reportFile = projectRoot.resolve(JacocoReportReader.REPORT_FILE);
        Files.createDirectories(reportFile.getParent());
        Files.writeString(reportFile, content);
    }

    @Test
    void readsReportLevelLineCoverage() throws IOException {
        writeReport("""
                <report name="demo">
                  <package name="com/example">
                    <counter type="LINE" missed="100" covered="0"/>
                  </package>
                  <counter type="INSTRUCTION" missed="5" covered="5"/>
                  <counter type="LINE" missed="25" covered="75"/>
                </report>
                """);

        assertEquals(75.0, reader.readLineCoverage(projectRoot).orElseThrow(), 0.001);
    }

    @Test
    void readsRealReportWithDoctypeDeclaration() throws IOException {
        writeReport("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
                <report name="demo"><counter type="LINE" missed="10" covered="90"/></report>
                """);

        assertEquals(90.0, reader.readLineCoverage(projectRoot).orElseThrow(), 0.001);
    }

    @Test
    void returnsEmptyWhenReportMissing() {
        assertTrue(reader.readLineCoverage(projectRoot).isEmpty());
    }

    @Test
    void returnsEmptyWhenReportInvalid() throws IOException {
        writeReport("<report name=\"broken\"");

        assertTrue(reader.readLineCoverage(projectRoot).isEmpty());
    }

    @Test
    void returnsEmptyWhenNoLineCounterPresent() throws IOException {
        writeReport("<report name=\"demo\"><counter type=\"INSTRUCTION\" missed=\"1\" covered=\"1\"/></report>");

        assertTrue(reader.readLineCoverage(projectRoot).isEmpty());
    }

    @Test
    void returnsEmptyWhenCounterHasNoLines() throws IOException {
        writeReport("<report name=\"demo\"><counter type=\"LINE\" missed=\"0\" covered=\"0\"/></report>");

        assertTrue(reader.readLineCoverage(projectRoot).isEmpty());
    }
}
