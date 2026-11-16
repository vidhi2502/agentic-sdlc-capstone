package com.docsync.output;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Behaviour tests for explicit console overwrite confirmation.
 */
class ConsoleUserPromptTest {

    @Test
    void acceptsYesIgnoringCaseAndWhitespace() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleUserPrompt prompt = prompt("  YeS  \n", output);

        assertTrue(prompt.confirmOverwrite(Path.of("DOCUMENTATION.md")));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Overwrite DOCUMENTATION.md?"));
    }

    @Test
    void declinesNoAndUnrecognizedResponses() throws Exception {
        assertFalse(prompt("no\n", new ByteArrayOutputStream())
                .confirmOverwrite(Path.of("DOCUMENTATION.md")));
        assertFalse(prompt("maybe\n", new ByteArrayOutputStream())
                .confirmOverwrite(Path.of("DOCUMENTATION.md")));
    }

    @Test
    void declinesWhenInputEnds() throws Exception {
        assertFalse(prompt("", new ByteArrayOutputStream())
                .confirmOverwrite(Path.of("DOCUMENTATION.md")));
    }

    private ConsoleUserPrompt prompt(String response, ByteArrayOutputStream output) {
        BufferedReader input = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8));
        return new ConsoleUserPrompt(input, new PrintStream(output, true, StandardCharsets.UTF_8));
    }
}
