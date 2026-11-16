package com.docsync.report;

import com.docsync.model.TestResult;
import com.docsync.xml.SecureXmlParser;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
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
        return read(projectRoot, Instant.MIN);
    }

    /**
     * Aggregates reports modified after the given time, excluding stale
     * reports left by an earlier Maven invocation.
     *
     * @param projectRoot the folder being scanned
     * @param modifiedAfter reports must have a later modification time
     * @return summed test counts, or empty when no fresh report could be read
     */
    public Optional<TestResult> read(Path projectRoot, Instant modifiedAfter) {
        Objects.requireNonNull(modifiedAfter, "modifiedAfter");
        Path reportsDir = projectRoot.resolve(REPORTS_DIR);
        if (!Files.isDirectory(reportsDir)) {
            return Optional.empty();
        }
        long total = 0;
        long failed = 0;
        long skipped = 0;
        boolean anyParsed = false;
        try (DirectoryStream<Path> reports = Files.newDirectoryStream(reportsDir, "*.xml")) {
            for (Path report : reports) {
                if (!modifiedAfter(report, modifiedAfter)) {
                    continue;
                }
                Optional<TestSuiteCounts> parsed = parseTestsuite(report);
                if (parsed.isPresent()) {
                    TestSuiteCounts counts = parsed.orElseThrow();
                    total += counts.total();
                    failed += counts.failed();
                    skipped += counts.skipped();
                    anyParsed = true;
                }
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        if (!anyParsed
                || total > Integer.MAX_VALUE
                || failed > Integer.MAX_VALUE
                || skipped > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        long passed = total - failed - skipped;
        return Optional.of(new TestResult(
                (int) total, (int) passed, (int) failed, (int) skipped, null));
    }

    private boolean modifiedAfter(Path report, Instant instant) {
        try {
            return Files.getLastModifiedTime(report).toInstant().isAfter(instant);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Parses one report and validates the suite's test counters.
     *
     * @return counts when the report has consistent non-negative values
     */
    private Optional<TestSuiteCounts> parseTestsuite(Path report) {
        try {
            Element root = SecureXmlParser.parse(report).getDocumentElement();
            if (!root.getNodeName().equals("testsuite")) {
                return Optional.empty();
            }
            OptionalInt total = nonNegativeIntAttribute(root, "tests");
            OptionalInt failures = nonNegativeIntAttribute(root, "failures");
            OptionalInt errors = nonNegativeIntAttribute(root, "errors");
            OptionalInt skipped = nonNegativeIntAttribute(root, "skipped");
            if (total.isEmpty() || failures.isEmpty() || errors.isEmpty() || skipped.isEmpty()) {
                return Optional.empty();
            }
            long failed = (long) failures.getAsInt() + errors.getAsInt();
            if (failed + skipped.getAsInt() > total.getAsInt()) {
                return Optional.empty();
            }
            return Optional.of(new TestSuiteCounts(total.getAsInt(), failed, skipped.getAsInt()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private OptionalInt nonNegativeIntAttribute(Element element, String name) {
        try {
            int value = Integer.parseInt(element.getAttribute(name));
            return value < 0 ? OptionalInt.empty() : OptionalInt.of(value);
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private record TestSuiteCounts(int total, long failed, int skipped) {
    }
}
