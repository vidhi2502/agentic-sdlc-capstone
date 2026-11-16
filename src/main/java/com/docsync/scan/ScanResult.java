package com.docsync.scan;

import com.docsync.model.ProjectInfo;
import java.util.List;
import java.util.Objects;

/**
 * Collected project information and non-fatal scan warnings.
 *
 * @param projectInfo assembled project snapshot
 * @param warnings    collector warnings safe to display on stderr
 */
public record ScanResult(ProjectInfo projectInfo, List<String> warnings) {

    /**
     * Validates the snapshot and defensively copies warnings.
     */
    public ScanResult {
        Objects.requireNonNull(projectInfo, "projectInfo");
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
