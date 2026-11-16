package com.docsync.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.docsync.security.SecretGuard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behaviour tests for {@link EntryPointScanner} detection, degradation and
 * secret skipping (FR-7, FR-9, NFR-1, NFR-5).
 */
class EntryPointScannerTest {

    private final EntryPointScanner scanner = new EntryPointScanner(new SecretGuard());

    @TempDir
    Path projectRoot;

    private Path writeSource(String relativePath, String content) throws IOException {
        Path file = projectRoot.resolve(EntryPointScanner.SOURCE_ROOT).resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    @Test
    void findsEntryPointsWithFullyQualifiedNames() throws IOException {
        writeSource("com/example/Main.java", """
                package com.example;
                public class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        writeSource("TopLevel.java", """
                public class TopLevel {
                    static public void main(String... args) {
                    }
                }
                """);
        writeSource("com/example/Helper.java", """
                package com.example;
                public class Helper {
                    public void run(String[] args) {
                    }
                }
                """);

        EntryPointResult result = scanner.scan(projectRoot);

        assertEquals(List.of("TopLevel", "com.example.Main"), result.entryPoints());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void returnsEmptyWhenNoClassHasMain() throws IOException {
        writeSource("com/example/Util.java", """
                package com.example;
                public class Util {
                }
                """);

        EntryPointResult result = scanner.scan(projectRoot);

        assertTrue(result.entryPoints().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void returnsNotFoundWhenSourceDirectoryMissing() {
        EntryPointResult result = scanner.scan(projectRoot);

        assertTrue(result.entryPoints().isEmpty());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void skipsNonJavaAndSecretFilesEvenWithMainSignature() throws IOException {
        String mainSignature = "public static void main(String[] args) {}";
        writeSource("server.pem", mainSignature);
        writeSource("api.key", mainSignature);
        writeSource(".env", mainSignature);
        writeSource("notes.txt", mainSignature);

        EntryPointResult result = scanner.scan(projectRoot);

        assertTrue(result.entryPoints().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
}
