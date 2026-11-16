package com.docsync.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behaviour tests for output location and overwrite policy.
 */
class OutputWriterTest {

    @TempDir
    Path projectRoot;

    @Test
    void createsDocumentationWhenItDoesNotExist() throws IOException {
        OutputWriter writer = new OutputWriter(new ScriptedUserPrompt(List.of()));

        OutputWriteResult result = writer.write(projectRoot, "new content", false, false);

        assertEquals(OutputWriteResult.Status.WRITTEN, result.status());
        assertEquals("new content", Files.readString(projectRoot.resolve("DOCUMENTATION.md")));
        assertTrue(writer.documentationExists(projectRoot));
    }

    @Test
    void forceOverwritesExistingDocumentationWithoutPrompting() throws IOException {
        Path output = writeExisting("old content");
        ScriptedUserPrompt prompt = new ScriptedUserPrompt(List.of());
        OutputWriter writer = new OutputWriter(prompt);

        OutputWriteResult result = writer.write(projectRoot, "new content", true, false);

        assertEquals(OutputWriteResult.Status.WRITTEN, result.status());
        assertEquals("new content", Files.readString(output));
        assertTrue(prompt.promptedFiles().isEmpty());
    }

    @Test
    void leavesExistingDocumentationUnchangedWhenPromptIsDeclined() throws IOException {
        Path output = writeExisting("keep this");
        OutputWriter writer = new OutputWriter(new ScriptedUserPrompt(List.of(false)));

        OutputWriteResult result = writer.write(projectRoot, "replace this", false, true);

        assertEquals(OutputWriteResult.Status.DECLINED, result.status());
        assertEquals("keep this", Files.readString(output));
    }

    @Test
    void promptsAndOverwritesWhenConfirmed() throws IOException {
        Path output = writeExisting("old content");
        ScriptedUserPrompt prompt = new ScriptedUserPrompt(List.of(true));
        OutputWriter writer = new OutputWriter(prompt);

        OutputWriteResult result = writer.write(projectRoot, "new content", false, true);

        assertEquals(OutputWriteResult.Status.WRITTEN, result.status());
        assertEquals("new content", Files.readString(output));
        assertEquals(List.of(output), prompt.promptedFiles());
    }

    @Test
    void abortsNonInteractiveOverwriteWithWarning() throws IOException {
        Path output = writeExisting("keep this");
        OutputWriter writer = new OutputWriter(new ScriptedUserPrompt(List.of()));

        OutputWriteResult result = writer.write(projectRoot, "replace this", false, false);

        assertEquals(OutputWriteResult.Status.ABORTED, result.status());
        assertFalse(result.warnings().isEmpty());
        assertEquals("keep this", Files.readString(output));
    }

    @Test
    void doesNotReplaceFileCreatedAfterTheExistenceCheck() throws IOException {
        Path output = writeExisting("keep this");
        OutputWriter writer = new OutputWriter(new ScriptedUserPrompt(List.of()));

        assertThrows(
                java.nio.file.FileAlreadyExistsException.class,
                () -> writer.writeConfirmed(projectRoot, "replace this", false));
        assertEquals("keep this", Files.readString(output));
    }

    private Path writeExisting(String content) throws IOException {
        Path output = projectRoot.resolve("DOCUMENTATION.md");
        Files.writeString(output, content);
        return output;
    }
}
