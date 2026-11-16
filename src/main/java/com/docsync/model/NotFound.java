package com.docsync.model;

/**
 * Central definition of the literal {@code Not Found} text required by FR-9 and
 * a helper for substituting it when a value is missing.
 */
public final class NotFound {

    /** The literal text rendered for any value that cannot be determined. */
    public static final String TEXT = "Not Found";

    private NotFound() {
    }

    /**
     * Returns the given value, or the literal {@link #TEXT} when the value is
     * {@code null} or blank.
     *
     * @param value a possibly missing value
     * @return the value itself, or {@code "Not Found"} when missing
     */
    public static String orNotFound(String value) {
        return value == null || value.isBlank() ? TEXT : value;
    }
}
