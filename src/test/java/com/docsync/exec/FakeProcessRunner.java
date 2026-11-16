package com.docsync.exec;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Scripted {@link ProcessRunner} for tests (DR-4). Returns a preconfigured
 * result or throws a preconfigured launch failure, while recording the
 * invocation for assertions.
 */
public final class FakeProcessRunner implements ProcessRunner {

    private final ProcessResult result;
    private final IOException launchFailure;
    private final List<List<String>> commands = new ArrayList<>();
    private final List<Path> workingDirectories = new ArrayList<>();
    private final List<Duration> timeouts = new ArrayList<>();

    private FakeProcessRunner(ProcessResult result, IOException launchFailure) {
        this.result = result;
        this.launchFailure = launchFailure;
    }

    /**
     * Creates a fake that always returns the given result.
     *
     * @param result the scripted outcome
     * @return the fake runner
     */
    public static FakeProcessRunner returning(ProcessResult result) {
        return new FakeProcessRunner(result, null);
    }

    /**
     * Creates a fake that always fails to launch, as when the executable is
     * missing from the PATH.
     *
     * @param message the launch failure message
     * @return the fake runner
     */
    public static FakeProcessRunner failingToLaunch(String message) {
        return new FakeProcessRunner(null, new IOException(message));
    }

    @Override
    public ProcessResult run(List<String> command, Path workingDirectory, Duration timeout) throws IOException {
        commands.add(List.copyOf(command));
        workingDirectories.add(workingDirectory);
        timeouts.add(timeout);
        if (launchFailure != null) {
            throw launchFailure;
        }
        return result;
    }

    /**
     * Returns every command passed to {@link #run}.
     *
     * @return recorded commands, oldest first
     */
    public List<List<String>> commands() {
        return List.copyOf(commands);
    }

    /**
     * Returns every working directory passed to {@link #run}.
     *
     * @return recorded working directories, oldest first
     */
    public List<Path> workingDirectories() {
        return List.copyOf(workingDirectories);
    }

    /**
     * Returns every timeout passed to {@link #run}.
     *
     * @return recorded timeouts, oldest first
     */
    public List<Duration> timeouts() {
        return List.copyOf(timeouts);
    }
}
