package com.docsync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.docsync.exec.FakeProcessRunner;
import com.docsync.exec.ProcessResult;
import com.docsync.exec.TestRunner;
import com.docsync.output.OutputWriter;
import com.docsync.output.ScriptedUserPrompt;
import com.docsync.render.MarkdownRenderer;
import com.docsync.report.JacocoReportReader;
import com.docsync.report.SurefireReportReader;
import com.docsync.scan.EntryPointScanner;
import com.docsync.scan.PomParser;
import com.docsync.scan.ScanOrchestrator;
import com.docsync.security.SecretGuard;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behaviour tests for CLI arguments, overwrite timing and exit codes.
 */
class DocSyncCliTest {

    @TempDir
    Path workingDirectory;

    @Test
    void defaultsToWorkingDirectoryAndSkipsTestsWhenRequested() throws Exception {
        writeProject();
        FakeProcessRunner process = successfulFakeProcess();
        DocSyncCli cli = cli(new ScriptedUserPrompt(List.of()), process);
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli.run(
                new String[] {"--skip-tests"},
                workingDirectory,
                printStream(stderr),
                false);

        String documentation = Files.readString(workingDirectory.resolve("DOCUMENTATION.md"));
        assertEquals(0, exitCode);
        assertTrue(process.commands().isEmpty());
        assertTrue(documentation.contains("## Project Name\nDemo"));
        assertTrue(documentation.contains("- Result: Not Found"));
        assertEquals("", stderr.toString(StandardCharsets.UTF_8));
    }

    @Test
    void promptsBeforeScanningAndLeavesFileUntouchedOnDecline() throws Exception {
        writeProject();
        Path output = workingDirectory.resolve("DOCUMENTATION.md");
        Files.writeString(output, "keep existing");
        ScriptedUserPrompt prompt = new ScriptedUserPrompt(List.of(false));
        FakeProcessRunner process = successfulFakeProcess();
        DocSyncCli cli = cli(prompt, process);

        int exitCode = cli.run(
                new String[] {"--skip-tests"},
                workingDirectory,
                printStream(new ByteArrayOutputStream()),
                true);

        assertEquals(0, exitCode);
        assertEquals("keep existing", Files.readString(output));
        assertTrue(process.commands().isEmpty());
        assertEquals(List.of(output), prompt.promptedFiles());
    }

    @Test
    void nonInteractiveOverwriteAbortsBeforeScanningWithWarning() throws Exception {
        writeProject();
        Path output = workingDirectory.resolve("DOCUMENTATION.md");
        Files.writeString(output, "keep existing");
        FakeProcessRunner process = successfulFakeProcess();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli(new ScriptedUserPrompt(List.of()), process).run(
                new String[] {"--skip-tests"},
                workingDirectory,
                printStream(stderr),
                false);

        assertEquals(1, exitCode);
        assertEquals("keep existing", Files.readString(output));
        assertTrue(process.commands().isEmpty());
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("use --force"));
    }

    @Test
    void forceOverwritesWithoutPrompting() throws Exception {
        writeProject();
        Path output = workingDirectory.resolve("DOCUMENTATION.md");
        Files.writeString(output, "old content");
        ScriptedUserPrompt prompt = new ScriptedUserPrompt(List.of());
        DocSyncCli cli = cli(prompt, successfulFakeProcess());

        int exitCode = cli.run(
                new String[] {workingDirectory.toString(), "--force", "--skip-tests"},
                workingDirectory,
                printStream(new ByteArrayOutputStream()),
                false);

        assertEquals(0, exitCode);
        assertFalse(Files.readString(output).contains("old content"));
        assertTrue(prompt.promptedFiles().isEmpty());
    }

    @Test
    void writesPartialDocumentationAndReturnsOneWhenCollectorsWarn() throws Exception {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        DocSyncCli cli = cli(new ScriptedUserPrompt(List.of()), successfulFakeProcess());

        int exitCode = cli.run(
                new String[] {"--skip-tests"},
                workingDirectory,
                printStream(stderr),
                false);

        assertEquals(1, exitCode);
        assertTrue(Files.exists(workingDirectory.resolve("DOCUMENTATION.md")));
        assertTrue(Files.readString(workingDirectory.resolve("DOCUMENTATION.md"))
                .contains("## Project Name\nNot Found"));
        assertFalse(stderr.toString(StandardCharsets.UTF_8).isBlank());
    }

    @Test
    void returnsTwoAndWarningForInvalidFolder() {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli(
                new ScriptedUserPrompt(List.of()),
                successfulFakeProcess()).run(
                new String[] {"missing-folder"},
                workingDirectory,
                printStream(stderr),
                false);

        assertEquals(2, exitCode);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Target folder"));
    }

    @Test
    void returnsTwoForUnsupportedArguments() {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = cli(
                new ScriptedUserPrompt(List.of()),
                successfulFakeProcess()).run(
                new String[] {"--unknown"},
                workingDirectory,
                printStream(stderr),
                false);

        assertEquals(2, exitCode);
        assertFalse(stderr.toString(StandardCharsets.UTF_8).isBlank());
    }

    @Test
    void redactsSecretsFromProjectMetadataAndNeverListsSecretFiles() throws Exception {
        Files.writeString(workingDirectory.resolve("pom.xml"), """
                <project>
                  <artifactId>demo</artifactId>
                  <name>Demo</name>
                  <description>token=embedded-secret</description>
                  <properties><maven.compiler.source>17</maven.compiler.source></properties>
                </project>
                """);
        Path sourceRoot = workingDirectory.resolve("src/main/java/example");
        Files.createDirectories(sourceRoot);
        Files.writeString(sourceRoot.resolve("Main.java"),
                "package example; public class Main { public static void main(String[] args) {} }");
        Files.writeString(sourceRoot.resolve(".env"), "AKIAIOSFODNN7EXAMPLE");
        Files.writeString(sourceRoot.resolve("credentials.key"), "key-file-secret");
        Files.writeString(sourceRoot.resolve("server.pem"), "pem-file-secret");
        DocSyncCli cli = cli(new ScriptedUserPrompt(List.of()), successfulFakeProcess());

        int exitCode = cli.run(
                new String[] {"--skip-tests"},
                workingDirectory,
                printStream(new ByteArrayOutputStream()),
                false);

        String documentation = Files.readString(workingDirectory.resolve("DOCUMENTATION.md"));
        assertEquals(0, exitCode);
        assertTrue(documentation.contains("[REDACTED]"));
        assertFalse(documentation.contains("embedded-secret"));
        assertFalse(documentation.contains("AKIAIOSFODNN7EXAMPLE"));
        assertFalse(documentation.contains("key-file-secret"));
        assertFalse(documentation.contains("pem-file-secret"));
        assertFalse(documentation.contains(".env"));
        assertFalse(documentation.contains("credentials.key"));
    }

    private DocSyncCli cli(ScriptedUserPrompt prompt, FakeProcessRunner process) {
        SecretGuard secretGuard = new SecretGuard();
        TestRunner testRunner = new TestRunner(
                process, new SurefireReportReader(), new JacocoReportReader());
        ScanOrchestrator orchestrator = new ScanOrchestrator(
                new PomParser(), new EntryPointScanner(secretGuard), testRunner, secretGuard);
        return new DocSyncCli(
                orchestrator,
                new MarkdownRenderer(secretGuard),
                new OutputWriter(prompt),
                prompt,
                secretGuard);
    }

    private FakeProcessRunner successfulFakeProcess() {
        return FakeProcessRunner.returning(new ProcessResult(0, "", false));
    }

    private void writeProject() throws Exception {
        Files.writeString(workingDirectory.resolve("pom.xml"), """
                <project>
                  <artifactId>demo</artifactId>
                  <name>Demo</name>
                  <properties><maven.compiler.source>17</maven.compiler.source></properties>
                </project>
                """);
        Path main = workingDirectory.resolve("src/main/java/example/Main.java");
        Files.createDirectories(main.getParent());
        Files.writeString(main, """
                package example;
                public class Main {
                    public static void main(String[] args) {}
                }
                """);
    }

    private PrintStream printStream(ByteArrayOutputStream output) {
        return new PrintStream(output, true, StandardCharsets.UTF_8);
    }
}
