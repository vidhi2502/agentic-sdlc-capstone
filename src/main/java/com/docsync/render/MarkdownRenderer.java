package com.docsync.render;

import com.docsync.model.Dependency;
import com.docsync.model.ProjectInfo;
import com.docsync.model.TestResult;
import com.docsync.security.SecretGuard;
import java.util.Objects;

/**
 * Renders the required six project documentation sections and redacts
 * token-like values before returning Markdown (FR-2, FR-9, NFR-2).
 */
public final class MarkdownRenderer {

    private final SecretGuard secretGuard;

    /**
     * Creates a renderer using the supplied central secret policy.
     *
     * @param secretGuard output redaction policy
     */
    public MarkdownRenderer(SecretGuard secretGuard) {
        this.secretGuard = Objects.requireNonNull(secretGuard, "secretGuard");
    }

    /**
     * Renders a project snapshot as Markdown with exactly six data sections.
     *
     * @param projectInfo collected project data
     * @return redacted Markdown
     */
    public String render(ProjectInfo projectInfo) {
        Objects.requireNonNull(projectInfo, "projectInfo");
        StringBuilder markdown = new StringBuilder("# Project Documentation\n\n");
        markdown.append("## Project Name\n")
                .append(scalar(projectInfo.nameOrNotFound()))
                .append("\n\n## Description\n")
                .append(scalar(projectInfo.descriptionOrNotFound()))
                .append("\n\n## Tech Stack\n")
                .append("- Java: ").append(scalar(projectInfo.javaVersionOrNotFound())).append('\n')
                .append("- Build tool: ").append(scalar(projectInfo.buildToolOrNotFound()))
                .append("\n\n## Dependencies\n");

        if (projectInfo.dependencies().isEmpty()) {
            markdown.append("Not Found\n");
        } else {
            for (Dependency dependency : projectInfo.dependencies()) {
                markdown.append("- ")
                        .append(scalar(dependency.groupArtifactOrNotFound()))
                        .append(" — ")
                        .append(scalar(dependency.versionOrNotFound()))
                        .append(" (")
                        .append(scalar(dependency.scope()))
                        .append(")\n");
            }
        }

        markdown.append("\n## Entry Points\n");
        if (projectInfo.entryPoints().isEmpty()) {
            markdown.append("Not Found\n");
        } else {
            for (String entryPoint : projectInfo.entryPoints()) {
                markdown.append("- `").append(scalar(entryPoint)).append("`\n");
            }
        }

        markdown.append("\n## Test Status\n");
        TestResult testResult = projectInfo.testResultOptional().orElse(null);
        if (testResult == null) {
            markdown.append("- Result: Not Found\n")
                    .append("- Tests: Not Found\n")
                    .append("- Line coverage: Not Found\n");
        } else {
            markdown.append("- Result: ").append(testResult.status()).append('\n')
                    .append("- Tests: total ").append(testResult.total())
                    .append(", passed ").append(testResult.passed())
                    .append(", failed ").append(testResult.failed())
                    .append(", skipped ").append(testResult.skipped()).append('\n')
                    .append("- Line coverage: ").append(testResult.coverageOrNotFound()).append('\n');
        }
        return secretGuard.redact(markdown.toString());
    }

    private String scalar(String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }
}
