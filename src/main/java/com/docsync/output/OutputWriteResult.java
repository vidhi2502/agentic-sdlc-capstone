package com.docsync.output;

import java.util.List;

/**
 * Outcome of writing generated project documentation.
 *
 * @param status   whether output was written, declined, or aborted
 * @param warnings non-fatal warnings for an aborted write
 */
public record OutputWriteResult(Status status, List<String> warnings) {

    /**
     * Possible outcomes of a documentation write.
     */
    public enum Status {
        WRITTEN,
        DECLINED,
        ABORTED
    }

    /**
     * Normalizes nullable warnings and defensively copies them.
     */
    public OutputWriteResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
