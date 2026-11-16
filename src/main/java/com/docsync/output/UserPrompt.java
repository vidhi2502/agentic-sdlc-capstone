package com.docsync.output;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Confirmation seam for operations that may overwrite user files (DR-4).
 */
public interface UserPrompt {

    /**
     * Asks whether the existing documentation file may be replaced.
     *
     * @param outputFile path to the existing file
     * @return {@code true} only when the user confirms
     * @throws IOException if reading the response fails
     */
    boolean confirmOverwrite(Path outputFile) throws IOException;
}
