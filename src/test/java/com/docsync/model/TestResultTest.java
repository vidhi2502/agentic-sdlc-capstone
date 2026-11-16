package com.docsync.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Behaviour tests for {@link TestResult} status derivation and coverage
 * fallback (FR-8, FR-9, DR-9).
 */
class TestResultTest {

    @Test
    void reportsPassWhenNoTestFailed() {
        assertEquals("PASS", new TestResult(5, 5, 0, 0, null).status());
    }

    @Test
    void reportsFailWhenAnyTestFailed() {
        assertEquals("FAIL", new TestResult(5, 4, 1, 0, null).status());
    }

    @Test
    void formatsCoverageWithOneDecimal() {
        assertEquals("87.5%", new TestResult(5, 5, 0, 0, 87.5).coverageOrNotFound());
    }

    @Test
    void returnsNotFoundWhenCoverageMissing() {
        assertEquals("Not Found", new TestResult(5, 5, 0, 0, null).coverageOrNotFound());
    }
}
