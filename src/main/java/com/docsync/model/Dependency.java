package com.docsync.model;

/**
 * A single dependency declared in {@code pom.xml} (FR-6).
 *
 * @param groupArtifact the {@code groupId:artifactId} coordinate, may be {@code null} when unknown
 * @param version       the declared version, may be {@code null} when absent or unresolvable
 * @param scope         the declared scope; {@code null} or blank defaults to {@code compile}
 */
public record Dependency(String groupArtifact, String version, String scope) {

    /** Maven's default dependency scope, applied when none is declared. */
    public static final String DEFAULT_SCOPE = "compile";

    /**
     * Normalizes a missing scope to Maven's default {@code compile}.
     */
    public Dependency {
        if (scope == null || scope.isBlank()) {
            scope = DEFAULT_SCOPE;
        }
    }

    /**
     * Returns the {@code groupId:artifactId} coordinate or {@code Not Found}.
     *
     * @return coordinate text, never {@code null}
     */
    public String groupArtifactOrNotFound() {
        return NotFound.orNotFound(groupArtifact);
    }

    /**
     * Returns the declared version or {@code Not Found} when absent or
     * unresolvable (e.g. parent-managed versions, DR-7).
     *
     * @return version text, never {@code null}
     */
    public String versionOrNotFound() {
        return NotFound.orNotFound(version);
    }
}
