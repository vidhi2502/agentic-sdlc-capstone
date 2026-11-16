package com.docsync.report;

import com.docsync.xml.SecureXmlParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads the line coverage percentage from a JaCoCo XML report with the
 * hardened DOM parser (FR-8). The report exists only when the scanned project
 * binds the JaCoCo plugin to its test phase; an absent or invalid report
 * degrades to an empty result, rendered as {@code Not Found} (FR-9, DR-9).
 */
public final class JacocoReportReader {

    /** Relative location of the JaCoCo XML report inside a Maven project. */
    static final Path REPORT_FILE = Path.of("target", "site", "jacoco", "jacoco.xml");

    /**
     * Reads the overall line coverage from the project's JaCoCo report.
     *
     * @param projectRoot the folder being scanned
     * @return the line coverage percentage (0–100), or empty when the report
     *         is absent, invalid or has no line counter; never throws
     */
    public Optional<Double> readLineCoverage(Path projectRoot) {
        Path reportFile = projectRoot.resolve(REPORT_FILE);
        if (!Files.isRegularFile(reportFile)) {
            return Optional.empty();
        }
        Element report;
        try {
            report = SecureXmlParser.parseAllowingDoctype(reportFile).getDocumentElement();
        } catch (Exception e) {
            return Optional.empty();
        }
        return reportLevelLineCounter(report).flatMap(this::toPercentage);
    }

    /**
     * Finds the report-wide {@code <counter type="LINE">} among the direct
     * children of {@code <report>}; per-class and per-package counters nested
     * deeper are intentionally ignored.
     */
    private Optional<Element> reportLevelLineCounter(Element report) {
        NodeList children = report.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && node.getNodeName().equals("counter")
                    && "LINE".equals(((Element) node).getAttribute("type"))) {
                return Optional.of((Element) node);
            }
        }
        return Optional.empty();
    }

    private Optional<Double> toPercentage(Element counter) {
        try {
            long missed = Long.parseLong(counter.getAttribute("missed"));
            long covered = Long.parseLong(counter.getAttribute("covered"));
            long total = missed + covered;
            if (total <= 0) {
                return Optional.empty();
            }
            return Optional.of(covered * 100.0 / total);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
