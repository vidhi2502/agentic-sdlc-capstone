package com.docsync.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Behaviour tests for {@link Dependency} scope defaulting and {@code Not Found}
 * fallbacks (FR-6, FR-9, DR-7).
 */
class DependencyTest {

    @Test
    void defaultsScopeToCompileWhenMissing() {
        assertEquals("compile", new Dependency("g:a", "1.0", null).scope());
        assertEquals("compile", new Dependency("g:a", "1.0", "  ").scope());
    }

    @Test
    void keepsDeclaredScope() {
        assertEquals("test", new Dependency("g:a", "1.0", "test").scope());
    }

    @Test
    void returnsNotFoundForMissingVersionOrCoordinate() {
        Dependency dependency = new Dependency(null, null, null);

        assertEquals("Not Found", dependency.groupArtifactOrNotFound());
        assertEquals("Not Found", dependency.versionOrNotFound());
    }

    @Test
    void returnsDeclaredVersionAndCoordinate() {
        Dependency dependency = new Dependency("org.junit:junit-jupiter", "5.10.2", "test");

        assertEquals("org.junit:junit-jupiter", dependency.groupArtifactOrNotFound());
        assertEquals("5.10.2", dependency.versionOrNotFound());
    }
}
