package com.docsync.scan;

import com.docsync.exec.TestRunResult;
import com.docsync.exec.TestRunner;
import com.docsync.model.ProjectInfo;
import com.docsync.model.TestResult;
import com.docsync.security.SecretGuard;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Coordinates the independent project collectors and degrades failed
 * collectors without losing information from the others (FR-9, FR-10, FR-11).
 */
public final class ScanOrchestrator {

    private final Function<Path, PomInfo> pomCollector;
    private final Function<Path, EntryPointResult> entryPointCollector;
    private final BiFunction<Path, Boolean, TestRunResult> testCollector;
    private final SecretGuard secretGuard;

    /**
     * Creates an orchestrator using the production collectors.
     *
     * @param pomParser         Maven metadata collector
     * @param entryPointScanner source entry-point collector
     * @param testRunner        Maven test collector
     * @param secretGuard       policy used to redact warnings
     */
    public ScanOrchestrator(
            PomParser pomParser,
            EntryPointScanner entryPointScanner,
            TestRunner testRunner,
            SecretGuard secretGuard) {
        this(
                Objects.requireNonNull(pomParser, "pomParser")::parse,
                Objects.requireNonNull(entryPointScanner, "entryPointScanner")::scan,
                Objects.requireNonNull(testRunner, "testRunner")::run,
                secretGuard);
    }

    ScanOrchestrator(
            Function<Path, PomInfo> pomCollector,
            Function<Path, EntryPointResult> entryPointCollector,
            BiFunction<Path, Boolean, TestRunResult> testCollector,
            SecretGuard secretGuard) {
        this.pomCollector = Objects.requireNonNull(pomCollector, "pomCollector");
        this.entryPointCollector = Objects.requireNonNull(entryPointCollector, "entryPointCollector");
        this.testCollector = Objects.requireNonNull(testCollector, "testCollector");
        this.secretGuard = Objects.requireNonNull(secretGuard, "secretGuard");
    }

    /**
     * Collects Maven metadata, source entry points and test status in sequence.
     *
     * @param projectRoot the project directory to scan
     * @param skipTests   whether the test process must be skipped
     * @return project information with accumulated warnings; never throws for
     *         a collector failure
     */
    public ScanResult scan(Path projectRoot, boolean skipTests) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        List<String> warnings = new ArrayList<>();

        PomInfo pomInfo;
        try {
            pomInfo = Objects.requireNonNull(pomCollector.apply(projectRoot), "pom result");
            addWarnings(warnings, pomInfo.warnings());
        } catch (RuntimeException e) {
            pomInfo = PomInfo.notFound("Unable to read pom.xml.");
            addWarnings(warnings, pomInfo.warnings());
        }

        EntryPointResult entryPointResult;
        try {
            entryPointResult = Objects.requireNonNull(
                    entryPointCollector.apply(projectRoot), "entry-point result");
            addWarnings(warnings, entryPointResult.warnings());
        } catch (RuntimeException e) {
            entryPointResult = EntryPointResult.notFound("Unable to scan source entry points.");
            addWarnings(warnings, entryPointResult.warnings());
        }

        TestRunResult testRunResult;
        try {
            testRunResult = Objects.requireNonNull(
                    testCollector.apply(projectRoot, skipTests), "test result");
            addWarnings(warnings, testRunResult.warnings());
        } catch (RuntimeException e) {
            testRunResult = new TestRunResult(
                    Optional.empty(), List.of("Unable to collect Maven test results."));
            addWarnings(warnings, testRunResult.warnings());
        }

        TestResult testResult = testRunResult.testResult().orElse(null);
        ProjectInfo projectInfo = new ProjectInfo(
                pomInfo.name(),
                pomInfo.description(),
                pomInfo.javaVersion(),
                pomInfo.buildTool(),
                pomInfo.dependencies(),
                entryPointResult.entryPoints(),
                testResult);
        return new ScanResult(projectInfo, warnings);
    }

    private void addWarnings(List<String> warnings, List<String> collectorWarnings) {
        for (String warning : collectorWarnings) {
            if (warning != null) {
                warnings.add(secretGuard.redact(warning));
            }
        }
    }
}
