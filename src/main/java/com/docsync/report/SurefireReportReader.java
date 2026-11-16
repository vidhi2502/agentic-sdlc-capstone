package com.docsync.report;

import com.docsync.model.TestResult;
import com.docsync.xml.SecureXmlParser;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.w3c.dom.Element;

/**
 * Reads Maven Surefire XML reports from {@code target/surefire-reports} with
 * the hardened DOM parser and aggregates them into test counts and a pass/fail
 * status (FR-8). Missing or entirely invalid reports degrade to an empty
 * result, rendered as {@code Not Found} (FR-9).
 */
public final class SurefireReportReader {

    /** Relative location of Surefire XML reports inside a Maven project. */
    static final Path REPORTS_DIR = Path.of("target", "surefire-reports");

    /**
     * Aggregates every readable Surefire report below the project root.
     *
     * @param projectRoot the folder being scanned
     * @return summed test counts without coverage, or empty when no report
     *         could be read; never throws
     */
    public Optional<TestResult> read(Path projectRoot) {
        Path reportsDir = projectRoot.resolve(REPORTS_DIR);
        if (!Files.isDirectory(reportsDir)) {
            return Optional.empty();
        }
        int total = 0;
        int failed = 0;
        int skipped = 0;
        boolean anyParsed = false;
        try (DirectoryStream<Path> reports = Files.newDirectoryStream(reportsDir, "*.xml")) {
            for (Path report : reports) {
                Element testsuite = parseTestsuite(report);
                if (testsuite != null) {
                    total += intAttribute(testsuite, "tests");
                    failed += intAttribute(testsuite, "failures") + intAttribute(testsuite, "errors");
                    skipped += intAttribute(testsuite, "skipped");
                    anyParsed = true;
                }
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        if (!anyParsed) {
            return Optional.empty();
        }
        int passed = total - failed - skipped;
        return Optional.of(new TestResult(total, passed, failed, skipped, null));
    }

    /**
     * Parses one report, returning its {@code <testsuite>} root or
     * {@code null} when the file is not a valid Surefire report.
     */
    private Element parseTestsuite(Path report) {
        try {
            Element root = SecureXmlParser.parse(report).getDocumentElement();
            return root.getNodeName().equals("testsuite") ? root : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int intAttribute(Element element, String name) {
        try {
            return Integer.parseInt(element.getAttribute(name));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
