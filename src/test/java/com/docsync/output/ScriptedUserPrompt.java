package com.docsync.output;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Scripted confirmation fake for tests of output-writing behavior.
 */
public final class ScriptedUserPrompt implements UserPrompt {

    private final List<Boolean> responses;
    private final List<Path> promptedFiles = new ArrayList<>();
    private int nextResponse;

    /**
     * Creates a fake that returns the supplied confirmations in order.
     *
     * @param responses scripted yes/no responses
     */
    public ScriptedUserPrompt(List<Boolean> responses) {
        this.responses = List.copyOf(responses);
    }

    /**
     * Returns the next scripted response for the given file.
     *
     * @param outputFile path for which confirmation is requested
     * @return the next scripted response
     * @throws IOException if no scripted response remains
     */
    @Override
    public boolean confirmOverwrite(Path outputFile) throws IOException {
        if (nextResponse >= responses.size()) {
            throw new IOException("No scripted prompt response remains.");
        }
        promptedFiles.add(outputFile);
        return responses.get(nextResponse++);
    }

    /**
     * Returns the files for which a prompt was requested, in call order.
     *
     * @return immutable prompt history
     */
    public List<Path> promptedFiles() {
        return List.copyOf(promptedFiles);
    }
}
