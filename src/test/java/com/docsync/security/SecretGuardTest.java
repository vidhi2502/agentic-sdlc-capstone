package com.docsync.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Behaviour tests for {@link SecretGuard} path exclusion (NFR-1) and output
 * redaction (NFR-2, DR-5).
 */
class SecretGuardTest {

    private final SecretGuard guard = new SecretGuard();

    @Test
    void excludesEnvKeyAndPemFiles() {
        assertTrue(guard.isExcluded(Path.of(".env")));
        assertTrue(guard.isExcluded(Path.of("config", ".env.local")));
        assertTrue(guard.isExcluded(Path.of("server.key")));
        assertTrue(guard.isExcluded(Path.of("certs", "ca.PEM")));
    }

    @Test
    void doesNotExcludeRegularFiles() {
        assertFalse(guard.isExcluded(Path.of("Main.java")));
        assertFalse(guard.isExcluded(Path.of("pom.xml")));
        assertFalse(guard.isExcluded(Path.of("environment.md")));
        assertFalse(guard.isExcluded(null));
    }

    @Test
    void redactsAwsAccessKeys() {
        String redacted = guard.redact("key AKIAIOSFODNN7EXAMPLE used");

        assertEquals("key [REDACTED] used", redacted);
    }

    @Test
    void redactsJwtShapedStrings() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

        assertEquals("[REDACTED]", guard.redact(jwt));
    }

    @Test
    void redactsSecretAssignments() {
        assertEquals("[REDACTED]", guard.redact("api_key=abc123"));
        assertEquals("[REDACTED]", guard.redact("PASSWORD: hunter2"));
        assertEquals("[REDACTED]", guard.redact("token = xyz"));
    }

    @Test
    void redactsPemPrivateKeyHeaders() {
        String redacted = guard.redact("-----BEGIN RSA PRIVATE KEY-----");

        assertEquals("[REDACTED]", redacted);
    }

    @Test
    void leavesCleanTextUnchanged() {
        String clean = "A normal description with org.junit:junit-jupiter 5.10.2";

        assertEquals(clean, guard.redact(clean));
        assertEquals("", guard.redact(""));
        assertNull(guard.redact(null));
    }
}
