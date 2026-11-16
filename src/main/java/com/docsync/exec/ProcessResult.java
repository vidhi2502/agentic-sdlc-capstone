package com.docsync.exec;

/**
 * Outcome of an external process run.
 *
 * @param exitCode the process exit code, or {@code -1} when the process was
 *                 killed after a timeout
 * @param output   merged stdout/stderr, capped at the last 64 KB (DR-6)
 * @param timedOut whether the process exceeded its timeout and was killed
 */
public record ProcessResult(int exitCode, String output, boolean timedOut) {

    /**
     * Convenience check for a clean run.
     *
     * @return {@code true} when the process finished in time with exit code 0
     */
    public boolean succeeded() {
        return !timedOut && exitCode == 0;
    }
}
