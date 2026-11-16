package com.docsync.exec;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Production {@link ProcessRunner} wrapping {@link ProcessBuilder}. Launches
 * with an argument list (no shell interpolation), merges stderr into stdout,
 * caps the captured output at the last 64 KB and kills the process after the
 * given timeout (DR-6).
 */
public final class DefaultProcessRunner implements ProcessRunner {

    /** Maximum number of captured output characters (last 64 KB). */
    static final int MAX_OUTPUT_CHARS = 64 * 1024;

    @Override
    public ProcessResult run(List<String> command, Path workingDirectory, Duration timeout)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        CappedOutputDrain drain = new CappedOutputDrain(process);
        Thread drainThread = new Thread(drain, "docsync-process-output-drain");
        drainThread.setDaemon(true);
        drainThread.start();
        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor();
                drainThread.join(1000);
                return new ProcessResult(-1, drain.tail(), true);
            }
            drainThread.join();
            return new ProcessResult(process.exitValue(), drain.tail(), false);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            throw e;
        }
    }

    /**
     * Drains the process output on a background thread (preventing pipe
     * deadlock) while keeping only the last {@link #MAX_OUTPUT_CHARS}
     * characters.
     */
    private static final class CappedOutputDrain implements Runnable {

        private final Process process;
        private final StringBuilder buffer = new StringBuilder();

        private CappedOutputDrain(Process process) {
            this.process = process;
        }

        @Override
        public void run() {
            char[] chunk = new char[8192];
            try (Reader reader = new InputStreamReader(process.getInputStream(), Charset.defaultCharset())) {
                int read;
                while ((read = reader.read(chunk)) != -1) {
                    append(chunk, read);
                }
            } catch (IOException e) {
                // The stream closes when the process is killed on timeout;
                // whatever was captured so far is the intended result.
            }
        }

        private synchronized void append(char[] chunk, int length) {
            buffer.append(chunk, 0, length);
            int excess = buffer.length() - MAX_OUTPUT_CHARS;
            if (excess > 0) {
                buffer.delete(0, excess);
            }
        }

        private synchronized String tail() {
            return buffer.toString();
        }
    }
}
