package com.docsync.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Behaviour tests for the scripted overwrite prompt fake.
 */
class ScriptedUserPromptTest {

    @Test
    void returnsResponsesInOrderAndTracksPromptedFiles() throws IOException {
        ScriptedUserPrompt prompt = new ScriptedUserPrompt(List.of(true, false));

        assertTrue(prompt.confirmOverwrite(Path.of("first.md")));
        assertFalse(prompt.confirmOverwrite(Path.of("second.md")));
        assertEquals(List.of(Path.of("first.md"), Path.of("second.md")), prompt.promptedFiles());
    }

    @Test
    void failsClearlyWhenNoScriptedResponseRemains() {
        ScriptedUserPrompt prompt = new ScriptedUserPrompt(List.of());

        assertThrows(IOException.class, () -> prompt.confirmOverwrite(Path.of("DOCUMENTATION.md")));
    }
}
