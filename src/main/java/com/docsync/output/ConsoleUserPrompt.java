package com.docsync.output;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Reads an overwrite confirmation from standard input.
 */
public final class ConsoleUserPrompt implements UserPrompt {

    private final BufferedReader input;
    private final PrintStream output;

    /**
     * Creates a prompt connected to the process standard input and output.
     */
    public ConsoleUserPrompt() {
        this(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)), System.out);
    }

    /**
     * Creates a prompt connected to the supplied streams.
     *
     * @param input  input reader for yes/no responses
     * @param output stream where the question is displayed
     */
    public ConsoleUserPrompt(BufferedReader input, PrintStream output) {
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
    }

    /**
     * Accepts only an explicit {@code y} or {@code yes}; other input and EOF
     * leave the existing file unchanged.
     *
     * @param outputFile path to the existing file
     * @return {@code true} only when the user enters yes
     * @throws IOException if reading the response fails
     */
    @Override
    public boolean confirmOverwrite(Path outputFile) throws IOException {
        Objects.requireNonNull(outputFile, "outputFile");
        output.printf("Overwrite %s? [y/N]: ", outputFile.getFileName());
        output.flush();
        String response = input.readLine();
        if (response == null) {
            return false;
        }
        String normalized = response.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("y") || normalized.equals("yes");
    }
}
