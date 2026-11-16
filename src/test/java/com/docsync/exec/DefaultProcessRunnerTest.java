package com.docsync.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behaviour tests for {@link DefaultProcessRunner}: real launch, output cap,
 * timeout kill and launch failure (FR-8, NFR-4, DR-6).
 */
class DefaultProcessRunnerTest {

    private final DefaultProcessRunner runner = new DefaultProcessRunner();

    @TempDir
    Path workingDirectory;

    /** Launches this JVM's java binary against the test classpath. */
    private List<String> javaCommand(String mode) {
        String javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return List.of(javaBinary, "-cp", System.getProperty("java.class.path"),
                TestProcessMain.class.getName(), mode);
    }

    @Test
    void capturesOutputAndExitCodeOfQuickProcess() throws Exception {
        ProcessResult result = runner.run(javaCommand("echo"), workingDirectory, Duration.ofMinutes(1));

        assertTrue(result.succeeded());
        assertEquals(0, result.exitCode());
        assertFalse(result.timedOut());
        assertTrue(result.output().contains("hello from child"));
    }

    @Test
    void capsCapturedOutputAtLastSixtyFourKb() throws Exception {
        ProcessResult result = runner.run(javaCommand("spam"), workingDirectory, Duration.ofMinutes(1));

        assertTrue(result.succeeded());
        assertTrue(result.output().length() <= DefaultProcessRunner.MAX_OUTPUT_CHARS,
                "output must be capped at 64 KB");
        assertTrue(result.output().endsWith("TAIL-MARKER"), "the cap must keep the tail");
    }

    @Test
    void killsProcessAfterTimeout() throws Exception {
        ProcessResult result = runner.run(javaCommand("sleep"), workingDirectory, Duration.ofSeconds(2));

        assertTrue(result.timedOut());
        assertFalse(result.succeeded());
        assertEquals(-1, result.exitCode());
    }

    @Test
    void throwsWhenExecutableCannotBeLaunched() {
        List<String> missing = List.of("docsync-no-such-executable-xyz");

        assertThrows(IOException.class,
                () -> runner.run(missing, workingDirectory, Duration.ofSeconds(5)));
    }
}
