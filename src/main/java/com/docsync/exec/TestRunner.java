package com.docsync.exec;

import com.docsync.model.TestResult;
import com.docsync.report.JacocoReportReader;
import com.docsync.report.SurefireReportReader;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Runs Maven tests and combines Surefire counts with optional JaCoCo coverage
 * (FR-8, DR-2, DR-6, OQ-3).
 */
public final class TestRunner {

    private static final Duration TEST_TIMEOUT = Duration.ofMinutes(5);
    private static final List<String> TEST_COMMAND = List.of("mvn", "test");

    private final ProcessRunner processRunner;
    private final SurefireReportReader surefireReportReader;
    private final JacocoReportReader jacocoReportReader;

    /**
     * Creates a runner using the provided external process seam and report
     * readers.
     *
     * @param processRunner         process launcher, usually {@link DefaultProcessRunner}
     * @param surefireReportReader  reader for Maven Surefire reports
     * @param jacocoReportReader    reader for JaCoCo coverage reports
     */
    public TestRunner(
            ProcessRunner processRunner,
            SurefireReportReader surefireReportReader,
            JacocoReportReader jacocoReportReader) {
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
        this.surefireReportReader = Objects.requireNonNull(surefireReportReader, "surefireReportReader");
        this.jacocoReportReader = Objects.requireNonNull(jacocoReportReader, "jacocoReportReader");
    }

    /**
     * Runs tests in the given project directory, unless testing was explicitly
     * skipped.
     *
     * @param projectRoot the Maven project directory
     * @param skipTests   whether the test process must not be launched
     * @return test information and warnings; unavailable test information is empty
     */
    public TestRunResult run(Path projectRoot, boolean skipTests) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        if (skipTests) {
            return new TestRunResult(Optional.empty(), List.of());
        }

        ProcessResult processResult;
        try {
            processResult = processRunner.run(TEST_COMMAND, projectRoot, TEST_TIMEOUT);
        } catch (IOException e) {
            return unavailable("Unable to start Maven tests.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unavailable("Maven test execution was interrupted.");
        }
        if (processResult.timedOut()) {
            return unavailable("Maven test execution timed out.");
        }

        Optional<TestResult> reported = surefireReportReader.read(projectRoot);
        if (reported.isEmpty()) {
            return unavailable("No readable Surefire test report was found.");
        }

        TestResult counts = reported.orElseThrow();
        Double coverage = jacocoReportReader.readLineCoverage(projectRoot).orElse(null);
        TestResult completeResult = new TestResult(
                counts.total(), counts.passed(), counts.failed(), counts.skipped(), coverage);
        return new TestRunResult(Optional.of(completeResult), List.of());
    }

    private TestRunResult unavailable(String warning) {
        return new TestRunResult(Optional.empty(), List.of(warning));
    }
}
