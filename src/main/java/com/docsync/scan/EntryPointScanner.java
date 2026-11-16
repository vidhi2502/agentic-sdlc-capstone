package com.docsync.scan;

import com.docsync.security.SecretGuard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds application entry points by scanning {@code src/main/java} for classes
 * containing a {@code public static void main(String[] args)} method (FR-7).
 * The walk never follows symbolic links (DR-8), reads only {@code *.java}
 * files (NFR-5) and consults {@link SecretGuard} before every read (NFR-1).
 */
public final class EntryPointScanner {

    /** Relative location of the production sources inside a Maven project. */
    static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    /**
     * Line-level signature match covering {@code public static} /
     * {@code static public}, {@code final} parameters, varargs and the
     * {@code String args[]} array-after-name form.
     */
    private static final Pattern MAIN_METHOD = Pattern.compile(
            "\\b(?:public\\s+static|static\\s+public)\\s+void\\s+main\\s*\\(\\s*(?:final\\s+)?"
                    + "String\\s*(?:\\[\\s*]\\s*\\w+|\\.\\.\\.\\s*\\w+|\\w+\\s*\\[\\s*])\\s*\\)");

    private final SecretGuard secretGuard;

    /**
     * Creates a scanner enforcing the given security policy.
     *
     * @param secretGuard the policy consulted before any file read
     */
    public EntryPointScanner(SecretGuard secretGuard) {
        this.secretGuard = secretGuard;
    }

    /**
     * Scans the project's {@code src/main/java} for entry-point classes.
     *
     * @param projectRoot the folder being scanned
     * @return fully qualified entry-point class names, or a degraded result
     *         with a warning when the source directory is missing; never throws
     */
    public EntryPointResult scan(Path projectRoot) {
        Path sourceRoot = projectRoot.resolve(SOURCE_ROOT);
        if (!Files.isDirectory(sourceRoot)) {
            return EntryPointResult.notFound("Source directory not found: " + sourceRoot);
        }
        List<String> entryPoints = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(this::isReadableJavaFile)
                    .forEach(file -> {
                        try {
                            if (containsMainMethod(file)) {
                                entryPoints.add(toClassName(sourceRoot, file));
                            }
                        } catch (IOException e) {
                            warnings.add("Could not read " + file + ": " + e.getMessage());
                        }
                    });
        } catch (IOException | SecurityException e) {
            warnings.add("Could not walk " + sourceRoot + ": " + e.getMessage());
        }
        entryPoints.sort(String::compareTo);
        return new EntryPointResult(entryPoints, warnings);
    }

    /**
     * Admits only regular, non-symlinked {@code *.java} files that are not
     * excluded by the security policy.
     */
    private boolean isReadableJavaFile(Path file) {
        return !secretGuard.isExcluded(file)
                && !Files.isSymbolicLink(file)
                && Files.isRegularFile(file)
                && file.getFileName().toString().endsWith(".java");
    }

    private boolean containsMainMethod(Path file) throws IOException {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.anyMatch(line -> MAIN_METHOD.matcher(line).find());
        }
    }

    /**
     * Derives the fully qualified class name from the file's location below
     * the source root, e.g. {@code com/example/Main.java → com.example.Main}.
     */
    private String toClassName(Path sourceRoot, Path file) {
        Path relative = sourceRoot.relativize(file);
        String joined = relative.toString()
                .replace(relative.getFileSystem().getSeparator(), ".");
        return joined.substring(0, joined.length() - ".java".length());
    }
}
