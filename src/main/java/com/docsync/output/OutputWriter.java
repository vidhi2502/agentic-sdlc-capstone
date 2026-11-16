package com.docsync.output;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

/**
 * Writes {@code DOCUMENTATION.md} and applies the overwrite policy
 * (FR-2, FR-3, DR-1, DR-10).
 */
public final class OutputWriter {

    /** Name of the generated documentation file. */
    public static final String DOCUMENTATION_FILE_NAME = "DOCUMENTATION.md";

    private final UserPrompt userPrompt;

    /**
     * Creates a writer with the supplied confirmation seam.
     *
     * @param userPrompt prompt used only when an interactive overwrite is needed
     */
    public OutputWriter(UserPrompt userPrompt) {
        this.userPrompt = Objects.requireNonNull(userPrompt, "userPrompt");
    }

    /**
     * Checks whether generated documentation already exists.
     *
     * @param projectRoot folder in which the documentation is written
     * @return {@code true} when {@code DOCUMENTATION.md} exists
     */
    public boolean documentationExists(Path projectRoot) {
        return Files.exists(documentationPath(projectRoot));
    }

    /**
     * Writes documentation using the standard overwrite rules.
     *
     * @param projectRoot  folder in which to write the documentation
     * @param markdown     rendered content
     * @param force        whether an existing file may be replaced silently
     * @param interactive  whether a user prompt is available
     * @return the write outcome and any warning
     * @throws IOException if the prompt or file write fails
     */
    public OutputWriteResult write(
            Path projectRoot, String markdown, boolean force, boolean interactive) throws IOException {
        Objects.requireNonNull(markdown, "markdown");
        Path outputFile = documentationPath(projectRoot);
        boolean outputExists = Files.exists(outputFile);
        if (outputExists && !force) {
            if (!interactive) {
                return new OutputWriteResult(
                        OutputWriteResult.Status.ABORTED,
                        List.of("DOCUMENTATION.md already exists; use --force to overwrite in non-interactive mode."));
            }
            if (!userPrompt.confirmOverwrite(outputFile)) {
                return new OutputWriteResult(OutputWriteResult.Status.DECLINED, List.of());
            }
        }
        writeConfirmed(projectRoot, markdown, force || outputExists);
        return new OutputWriteResult(OutputWriteResult.Status.WRITTEN, List.of());
    }

    /**
     * Writes content after an overwrite decision has already been made. This
     * path lets the CLI ask before scanning the project (DR-10). If replacement
     * was not authorized, a file created after the early check is preserved.
     *
     * @param projectRoot folder in which to write the documentation
     * @param markdown    rendered content
     * @param replaceExisting whether an existing file may be replaced
     * @throws IOException if the file cannot be written
     */
    public void writeConfirmed(Path projectRoot, String markdown, boolean replaceExisting) throws IOException {
        Objects.requireNonNull(markdown, "markdown");
        Path outputFile = documentationPath(projectRoot);
        if (replaceExisting) {
            Files.writeString(
                    outputFile,
                    markdown,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } else {
            Files.writeString(
                    outputFile,
                    markdown,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        }
    }

    private Path documentationPath(Path projectRoot) {
        return Objects.requireNonNull(projectRoot, "projectRoot").resolve(DOCUMENTATION_FILE_NAME);
    }
}
