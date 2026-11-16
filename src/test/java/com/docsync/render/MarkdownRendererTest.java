package com.docsync.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.docsync.model.Dependency;
import com.docsync.model.ProjectInfo;
import com.docsync.model.TestResult;
import com.docsync.security.SecretGuard;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Behaviour tests for Markdown section rendering and output redaction.
 */
class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer(new SecretGuard());

    @Test
    void rendersAllSixSectionsWithCollectedProjectInformation() {
        ProjectInfo info = new ProjectInfo(
                "Sample",
                "Example application",
                "17",
                "Maven",
                List.of(new Dependency("org.example:library", "2.1", "runtime")),
                List.of("org.example.Main"),
                new TestResult(5, 4, 1, 0, 80.0));

        String markdown = renderer.render(info);

        assertEquals(6, countOccurrences(markdown, "\n## "));
        assertTrue(markdown.startsWith("# Project Documentation\n"));
        assertTrue(markdown.contains("## Project Name\nSample"));
        assertTrue(markdown.contains("## Description\nExample application"));
        assertTrue(markdown.contains("- Java: 17"));
        assertTrue(markdown.contains("- Build tool: Maven"));
        assertTrue(markdown.contains("org.example:library — 2.1 (runtime)"));
        assertTrue(markdown.contains("`org.example.Main`"));
        assertTrue(markdown.contains("Result: FAIL"));
        assertTrue(markdown.contains("total 5, passed 4, failed 1, skipped 0"));
        assertTrue(markdown.contains("Line coverage: 80.0%"));
    }

    @Test
    void rendersNotFoundForMissingData() {
        String markdown = renderer.render(ProjectInfo.empty());

        assertTrue(markdown.contains("## Project Name\nNot Found"));
        assertTrue(markdown.contains("## Description\nNot Found"));
        assertTrue(markdown.contains("- Java: Not Found"));
        assertTrue(markdown.contains("- Build tool: Not Found"));
        assertTrue(markdown.contains("## Dependencies\nNot Found"));
        assertTrue(markdown.contains("## Entry Points\nNot Found"));
        assertTrue(markdown.contains("- Result: Not Found"));
        assertTrue(markdown.contains("- Tests: Not Found"));
        assertTrue(markdown.contains("- Line coverage: Not Found"));
        assertEquals(6, countOccurrences(markdown, "\n## "));
    }

    @Test
    void redactsSecretLikeValuesFromEveryRenderedField() {
        ProjectInfo info = new ProjectInfo(
                "api_key=abc123",
                "A description",
                "17",
                "Maven",
                List.of(),
                List.of(),
                null);

        String markdown = renderer.render(info);

        assertFalse(markdown.contains("abc123"));
        assertTrue(markdown.contains("[REDACTED]"));
    }

    private int countOccurrences(String text, String value) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }
}
