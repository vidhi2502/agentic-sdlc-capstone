package com.docsync.exec;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Test seam abstracting external process launch (DR-4), so {@code TestRunner}
 * is unit-testable without a real Maven installation. Implemented by
 * {@link DefaultProcessRunner} in production and by a scripted fake in tests.
 */
public interface ProcessRunner {

    /**
     * Runs the given command and waits for it to finish or time out.
     *
     * @param command          the executable and its arguments as a list; never
     *                         joined into a shell string, so arguments cannot
     *                         inject commands (architecture §6)
     * @param workingDirectory the directory the process starts in
     * @param timeout          the maximum time to wait before killing the process
     * @return the exit code, captured output and timeout flag
     * @throws IOException          when the process cannot be launched, e.g.
     *                              the executable is not on the PATH
     * @throws InterruptedException when the waiting thread is interrupted
     */
    ProcessResult run(List<String> command, Path workingDirectory, Duration timeout)
            throws IOException, InterruptedException;
}
