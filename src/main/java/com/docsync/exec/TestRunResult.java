package com.docsync.exec;

import com.docsync.model.TestResult;
import java.util.List;
import java.util.Optional;

/**
 * Parsed outcome and warnings from a project's test run.
 *
 * @param testResult parsed test counts and coverage, or empty when unavailable
 * @param warnings  non-fatal issues encountered while running or parsing tests
 */
public record TestRunResult(Optional<TestResult> testResult, List<String> warnings) {

    /**
     * Normalizes nullable values and defensively copies warnings.
     */
    public TestRunResult {
        testResult = testResult == null ? Optional.empty() : testResult;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
