package com.docsync.model;

import java.util.Locale;

/**
 * Outcome of running the scanned project's tests (FR-8).
 *
 * @param total               total number of tests run
 * @param passed              number of passing tests
 * @param failed              number of failed tests (including errors)
 * @param skipped             number of skipped tests
 * @param lineCoveragePercent line coverage percentage from the JaCoCo report,
 *                            or {@code null} when no report is available (DR-9)
 */
public record TestResult(int total, int passed, int failed, int skipped, Double lineCoveragePercent) {

    /**
     * Derives the overall status from the failure count.
     *
     * @return {@code "PASS"} when no test failed, otherwise {@code "FAIL"}
     */
    public String status() {
        return failed == 0 ? "PASS" : "FAIL";
    }

    /**
     * Returns the line coverage as display text, or {@code Not Found} when no
     * JaCoCo report was available.
     *
     * @return coverage text such as {@code "87.5%"}, never {@code null}
     */
    public String coverageOrNotFound() {
        if (lineCoveragePercent == null) {
            return NotFound.TEXT;
        }
        return String.format(Locale.ROOT, "%.1f%%", lineCoveragePercent);
    }
}
