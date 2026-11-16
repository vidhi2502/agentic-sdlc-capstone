package com.docsync.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Behaviour tests for the immutable data model and its {@code Not Found}
 * fallbacks (FR-2, FR-9).
 */
class ProjectInfoTest {

    @Test
    void returnsNotFoundForEveryMissingField() {
        ProjectInfo info = ProjectInfo.empty();

        assertEquals("Not Found", info.nameOrNotFound());
        assertEquals("Not Found", info.descriptionOrNotFound());
        assertEquals("Not Found", info.javaVersionOrNotFound());
        assertEquals("Not Found", info.buildToolOrNotFound());
        assertTrue(info.dependencies().isEmpty());
        assertTrue(info.entryPoints().isEmpty());
        assertTrue(info.testResultOptional().isEmpty());
    }

    @Test
    void returnsNotFoundForBlankValues() {
        ProjectInfo info = new ProjectInfo("  ", "", null, null, null, null, null);

        assertEquals("Not Found", info.nameOrNotFound());
        assertEquals("Not Found", info.descriptionOrNotFound());
    }

    @Test
    void returnsActualValuesWhenPresent() {
        TestResult result = new TestResult(10, 9, 1, 0, 85.0);
        ProjectInfo info = new ProjectInfo("demo", "A demo", "17", "Maven",
                List.of(new Dependency("g:a", "1.0", "test")),
                List.of("com.example.Main"), result);

        assertEquals("demo", info.nameOrNotFound());
        assertEquals("A demo", info.descriptionOrNotFound());
        assertEquals("17", info.javaVersionOrNotFound());
        assertEquals("Maven", info.buildToolOrNotFound());
        assertEquals(1, info.dependencies().size());
        assertEquals(List.of("com.example.Main"), info.entryPoints());
        assertEquals(result, info.testResultOptional().orElseThrow());
    }

    @Test
    void normalizesNullListsToEmptyAndStaysImmutable() {
        List<String> mutable = new ArrayList<>(List.of("com.example.Main"));
        ProjectInfo info = new ProjectInfo("demo", null, null, null, null, mutable, null);

        mutable.add("com.example.Other");

        assertEquals(List.of("com.example.Main"), info.entryPoints());
        assertTrue(info.dependencies().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> info.entryPoints().add("x"));
    }
}
