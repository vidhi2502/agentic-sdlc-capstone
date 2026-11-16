package com.docsync.scan;

import java.util.List;

/**
 * Result of scanning {@code src/main/java} for entry points (FR-7).
 *
 * @param entryPoints fully qualified names of classes declaring a
 *                    {@code public static void main(String[] args)} method;
 *                    empty when none were found
 * @param warnings    human-readable problems encountered while scanning,
 *                    empty on full success
 */
public record EntryPointResult(List<String> entryPoints, List<String> warnings) {

    /**
     * Normalizes {@code null} lists to empty immutable lists.
     */
    public EntryPointResult {
        entryPoints = entryPoints == null ? List.of() : List.copyOf(entryPoints);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /**
     * Creates a degraded result for a missing or unreadable source directory
     * (FR-9, FR-10).
     *
     * @param warning the problem to report on stderr later
     * @return an empty result carrying the given warning
     */
    public static EntryPointResult notFound(String warning) {
        return new EntryPointResult(List.of(), List.of(warning));
    }
}
