package com.docsync.model;

import java.util.List;
import java.util.Optional;

/**
 * Immutable snapshot of everything collected about a scanned project (FR-2).
 * Every field carries an explicit {@code Not Found} representation so the
 * renderer never deals with raw {@code null}s (FR-9).
 *
 * @param name        project name from {@code <name>} or {@code <artifactId>}, may be {@code null}
 * @param description project description, may be {@code null}
 * @param javaVersion detected Java version, may be {@code null}
 * @param buildTool   detected build tool, may be {@code null}
 * @param dependencies declared dependencies; empty when none could be determined
 * @param entryPoints fully qualified names of classes with a {@code main} method;
 *                    empty when none could be determined
 * @param testResult  test run outcome, or {@code null} when tests were not run
 *                    or produced no readable report
 */
public record ProjectInfo(
        String name,
        String description,
        String javaVersion,
        String buildTool,
        List<Dependency> dependencies,
        List<String> entryPoints,
        TestResult testResult) {

    /**
     * Normalizes {@code null} lists to empty immutable lists and defensively
     * copies the rest so the record stays deeply immutable.
     */
    public ProjectInfo {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        entryPoints = entryPoints == null ? List.of() : List.copyOf(entryPoints);
    }

    /**
     * Creates a {@code ProjectInfo} with every field missing, as produced for
     * an empty or unreadable folder (FR-10).
     *
     * @return a project info whose every rendered field is {@code Not Found}
     */
    public static ProjectInfo empty() {
        return new ProjectInfo(null, null, null, null, List.of(), List.of(), null);
    }

    /**
     * Returns the project name or {@code Not Found}.
     *
     * @return name text, never {@code null}
     */
    public String nameOrNotFound() {
        return NotFound.orNotFound(name);
    }

    /**
     * Returns the project description or {@code Not Found}.
     *
     * @return description text, never {@code null}
     */
    public String descriptionOrNotFound() {
        return NotFound.orNotFound(description);
    }

    /**
     * Returns the Java version or {@code Not Found}.
     *
     * @return Java version text, never {@code null}
     */
    public String javaVersionOrNotFound() {
        return NotFound.orNotFound(javaVersion);
    }

    /**
     * Returns the build tool or {@code Not Found}.
     *
     * @return build tool text, never {@code null}
     */
    public String buildToolOrNotFound() {
        return NotFound.orNotFound(buildTool);
    }

    /**
     * Returns the test result when tests were run and reported.
     *
     * @return the test result, or empty when the test status is {@code Not Found}
     */
    public Optional<TestResult> testResultOptional() {
        return Optional.ofNullable(testResult);
    }
}
