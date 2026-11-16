package com.docsync;

import com.docsync.exec.DefaultProcessRunner;
import com.docsync.exec.TestRunner;
import com.docsync.output.ConsoleUserPrompt;
import com.docsync.output.OutputWriter;
import com.docsync.output.UserPrompt;
import com.docsync.render.MarkdownRenderer;
import com.docsync.scan.EntryPointScanner;
import com.docsync.scan.PomParser;
import com.docsync.scan.ScanOrchestrator;
import com.docsync.scan.ScanResult;
import com.docsync.security.SecretGuard;
import com.docsync.report.JacocoReportReader;
import com.docsync.report.SurefireReportReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Command-line entry point for generating project documentation (FR-1, FR-3,
 * FR-11).
 */
public final class DocSyncCli {

    private final ScanOrchestrator scanOrchestrator;
    private final MarkdownRenderer markdownRenderer;
    private final OutputWriter outputWriter;
    private final UserPrompt userPrompt;
    private final SecretGuard secretGuard;

    /**
     * Creates a CLI using the supplied application components.
     *
     * @param scanOrchestrator collector coordinator
     * @param markdownRenderer documentation renderer
     * @param outputWriter     documentation file writer
     * @param userPrompt       interactive overwrite prompt
     * @param secretGuard      warning redaction policy
     */
    public DocSyncCli(
            ScanOrchestrator scanOrchestrator,
            MarkdownRenderer markdownRenderer,
            OutputWriter outputWriter,
            UserPrompt userPrompt,
            SecretGuard secretGuard) {
        this.scanOrchestrator = Objects.requireNonNull(scanOrchestrator, "scanOrchestrator");
        this.markdownRenderer = Objects.requireNonNull(markdownRenderer, "markdownRenderer");
        this.outputWriter = Objects.requireNonNull(outputWriter, "outputWriter");
        this.userPrompt = Objects.requireNonNull(userPrompt, "userPrompt");
        this.secretGuard = Objects.requireNonNull(secretGuard, "secretGuard");
    }

    /**
     * Parses command-line arguments and generates documentation.
     *
     * @param args             optional folder path and supported flags
     * @param workingDirectory current working directory used when no folder is given
     * @param stderr           destination for warnings
     * @param interactive      whether interactive overwrite confirmation is available
     * @return process exit code: 0 success/declined, 1 partial or aborted, 2 invalid path/invocation
     */
    public int run(String[] args, Path workingDirectory, PrintStream stderr, boolean interactive) {
        Objects.requireNonNull(stderr, "stderr");
        CliOptions options;
        try {
            options = parseArguments(args, workingDirectory);
        } catch (IllegalArgumentException e) {
            warn(stderr, e.getMessage());
            return 2;
        } catch (RuntimeException e) {
            warn(stderr, "The folder path or command-line arguments are invalid.");
            return 2;
        }

        if (!isDirectory(options.projectRoot())) {
            warn(stderr, "Target folder must exist and be a directory.");
            return 2;
        }

        try {
            boolean readable = isReadableDirectory(options.projectRoot());
            boolean outputExists = outputWriter.documentationExists(options.projectRoot());
            boolean replaceExisting = options.force();
            if (!readable) {
                warn(stderr, "Target folder is not readable; generating partial documentation where possible.");
            }
            if (outputExists && !options.force()) {
                if (!interactive) {
                    warn(stderr, "DOCUMENTATION.md already exists; use --force to overwrite in non-interactive mode.");
                    return 1;
                }
                boolean approved;
                try {
                    approved = userPrompt.confirmOverwrite(
                            options.projectRoot().resolve(OutputWriter.DOCUMENTATION_FILE_NAME));
                } catch (IOException e) {
                    warn(stderr, "Unable to read the overwrite confirmation.");
                    return 1;
                }
                if (!approved) {
                    return 0;
                }
                replaceExisting = true;
            }

            ScanResult scanResult = scanOrchestrator.scan(
                    options.projectRoot(), options.skipTests() || !readable);
            for (String warning : scanResult.warnings()) {
                warn(stderr, warning);
            }
            String markdown = markdownRenderer.render(scanResult.projectInfo());
            outputWriter.writeConfirmed(options.projectRoot(), markdown, replaceExisting);
            return readable && scanResult.warnings().isEmpty() ? 0 : 1;
        } catch (IOException e) {
            warn(stderr, "Unable to write DOCUMENTATION.md.");
            return 1;
        } catch (RuntimeException e) {
            warn(stderr, "Documentation generation failed.");
            return 1;
        }
    }

    /**
     * Launches the CLI using the current working directory and system console.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        int exitCode;
        try {
            SecretGuard secretGuard = new SecretGuard();
            UserPrompt userPrompt = new ConsoleUserPrompt();
            OutputWriter outputWriter = new OutputWriter(userPrompt);
            ScanOrchestrator scanOrchestrator = new ScanOrchestrator(
                    new PomParser(),
                    new EntryPointScanner(secretGuard),
                    new TestRunner(
                            new DefaultProcessRunner(),
                            new SurefireReportReader(),
                            new JacocoReportReader()),
                    secretGuard);
            DocSyncCli cli = new DocSyncCli(
                    scanOrchestrator,
                    new MarkdownRenderer(secretGuard),
                    outputWriter,
                    userPrompt,
                    secretGuard);
            exitCode = cli.run(
                    args,
                    Path.of("").toAbsolutePath(),
                    System.err,
                    System.console() != null);
        } catch (RuntimeException e) {
            System.err.println("Documentation generation failed.");
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    private CliOptions parseArguments(String[] args, Path workingDirectory) {
        if (args == null) {
            throw new IllegalArgumentException("Arguments are required.");
        }
        if (workingDirectory == null) {
            throw new IllegalArgumentException("Working directory is required.");
        }
        Path baseDirectory = workingDirectory.toAbsolutePath().normalize();
        String folderArgument = null;
        boolean force = false;
        boolean skipTests = false;
        boolean forceSeen = false;
        boolean skipTestsSeen = false;

        for (String argument : args) {
            if (argument == null) {
                throw new IllegalArgumentException("Arguments must not contain null values.");
            }
            if (argument.equals("--force")) {
                if (forceSeen) {
                    throw new IllegalArgumentException("The --force option may be specified only once.");
                }
                force = true;
                forceSeen = true;
            } else if (argument.equals("--skip-tests")) {
                if (skipTestsSeen) {
                    throw new IllegalArgumentException("The --skip-tests option may be specified only once.");
                }
                skipTests = true;
                skipTestsSeen = true;
            } else if (argument.startsWith("-")) {
                throw new IllegalArgumentException("Unsupported command-line option.");
            } else if (folderArgument != null) {
                throw new IllegalArgumentException("Only one folder path may be specified.");
            } else {
                folderArgument = argument;
            }
        }

        Path projectRoot;
        try {
            Path requested = folderArgument == null ? baseDirectory : Path.of(folderArgument);
            projectRoot = requested.isAbsolute() ? requested : baseDirectory.resolve(requested);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("The folder path is invalid.");
        }
        return new CliOptions(projectRoot.toAbsolutePath().normalize(), force, skipTests);
    }

    private boolean isDirectory(Path path) {
        try {
            return Files.isDirectory(path);
        } catch (SecurityException e) {
            return false;
        }
    }

    private boolean isReadableDirectory(Path path) {
        try {
            return Files.isReadable(path);
        } catch (SecurityException e) {
            return false;
        }
    }

    private void warn(PrintStream stderr, String message) {
        stderr.println(secretGuard.redact(message));
    }

    private record CliOptions(Path projectRoot, boolean force, boolean skipTests) {
    }
}
