package com.docsync.security;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Central security policy (NFR-1, NFR-2, DR-5): path exclusion for secret
 * files and output redaction for token-like values. All file-system traversal
 * and all rendered output must pass through this class so the policy cannot be
 * bypassed by an individual collector.
 */
public final class SecretGuard {

    /** Replacement text for every redacted match. */
    public static final String REDACTED = "[REDACTED]";

    /**
     * Finalized explicit redaction pattern set (DR-5): AWS access keys,
     * JWT-shaped base64 triplets, api-key/token/secret/password assignments,
     * and PEM private-key headers.
     */
    private static final List<Pattern> REDACTION_PATTERNS = List.of(
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"),
            Pattern.compile("(?i)(api[_-]?key|token|secret|password)\\s*[=:]\\s*\\S+"),
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"));

    /**
     * Decides whether a path must never be opened or read (NFR-1). Matches
     * {@code .env} files (including suffixed variants such as
     * {@code .env.local}), {@code *.key} and {@code *.pem}, case-insensitively.
     *
     * @param path the path to check; may be a file or directory entry
     * @return {@code true} when the path is a secret file that must be skipped
     */
    public boolean isExcluded(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.equals(".env")
                || name.startsWith(".env.")
                || name.endsWith(".env")
                || name.endsWith(".key")
                || name.endsWith(".pem");
    }

    /**
     * Replaces every token-like match in the given text with
     * {@link #REDACTED} (NFR-2, DR-5).
     *
     * @param text the text to scrub; {@code null} is returned unchanged
     * @return the text with all matches replaced, never leaking the original value
     */
    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (Pattern pattern : REDACTION_PATTERNS) {
            result = pattern.matcher(result).replaceAll(REDACTED);
        }
        return result;
    }
}
