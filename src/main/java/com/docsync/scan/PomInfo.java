package com.docsync.scan;

import com.docsync.model.Dependency;
import java.util.List;

/**
 * Everything {@link PomParser} could extract from {@code pom.xml}
 * (FR-4, FR-5, FR-6). Missing values are {@code null} and render as
 * {@code Not Found} downstream (FR-9).
 *
 * @param name         project name from {@code <name>} or {@code <artifactId>}, may be {@code null}
 * @param description  project description, may be {@code null}
 * @param javaVersion  Java version from {@code maven.compiler.source} or {@code java.version}, may be {@code null}
 * @param buildTool    {@code "Maven"} when a pom was parsed, otherwise {@code null}
 * @param dependencies declared dependencies, empty when none were found
 * @param warnings     human-readable problems encountered while parsing, empty on full success
 */
public record PomInfo(
        String name,
        String description,
        String javaVersion,
        String buildTool,
        List<Dependency> dependencies,
        List<String> warnings) {

    /**
     * Normalizes {@code null} lists to empty immutable lists.
     */
    public PomInfo {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /**
     * Creates a result with every field missing, used when {@code pom.xml} is
     * absent or unparseable (FR-9, FR-10).
     *
     * @param warning the problem to report on stderr later
     * @return a fully degraded result carrying the given warning
     */
    public static PomInfo notFound(String warning) {
        return new PomInfo(null, null, null, null, List.of(), List.of(warning));
    }
}
