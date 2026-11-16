## Summary

Adds a Java 17 Maven CLI that scans a project and writes `DOCUMENTATION.md` with project metadata, dependencies, entry points, test results, and available coverage while handling missing data and protecting secrets. The repository also includes the artifacts and custom agents for an eight-stage agentic SDLC pipeline.

## Changes Made

- Added `CHANGELOG.md` to record the unreleased CLI and SDLC pipeline features.
- Added pipeline agent definitions: `.github/agents/architecture-agent.agent.md`, `.github/agents/code-review-agent.agent.md`, `.github/agents/design-review-agent.agent.md`, `.github/agents/implementation-agent.agent.md`, `.github/agents/orchestrator.agent.md`, `.github/agents/planning-agent.agent.md`, `.github/agents/pr-agent.agent.md`, `.github/agents/requirements-agent.agent.md`, and `.github/agents/verification-agent.agent.md`.
- Added Copilot instructions, Java rules, hooks, scripts, prompts, and skills: `.github/copilot-instructions.md`, `.github/instructions/java.instructions.md`, `.github/hooks/hooks.json`, `.github/hooks/scripts/Guard.ps1`, `.github/hooks/scripts/Log.ps1`, `.github/prompts/run-pipeline.prompt.md`, `.github/prompts/status.prompt.md`, `.github/skills/architecture-template/SKILL.md`, `.github/skills/code-review-checklist/SKILL.md`, `.github/skills/pr-template/SKILL.md`, and `.github/skills/requirements-template/SKILL.md`.
- Added repository setup and story files: `.gitignore`, `pom.xml`, and `input/user-story.md`.
- Updated `README.md` with build and usage instructions, exit codes, behavior/security notes, and the pipeline overview.
- Added SDLC documentation: `docs/requirements.md`, `docs/architecture.md`, `docs/design-review.md`, `docs/impl-plan.md`, `docs/code-review.md`, `docs/verification-report.md`, `docs/pipeline-log.md`, and `docs/pipeline-state.md`.
- Added the CLI and execution layer: `src/main/java/com/docsync/DocSyncCli.java`, `src/main/java/com/docsync/exec/DefaultProcessRunner.java`, `src/main/java/com/docsync/exec/ProcessResult.java`, `src/main/java/com/docsync/exec/ProcessRunner.java`, `src/main/java/com/docsync/exec/TestRunResult.java`, and `src/main/java/com/docsync/exec/TestRunner.java`.
- Added data and output components: `src/main/java/com/docsync/model/Dependency.java`, `src/main/java/com/docsync/model/NotFound.java`, `src/main/java/com/docsync/model/ProjectInfo.java`, `src/main/java/com/docsync/model/TestResult.java`, `src/main/java/com/docsync/output/ConsoleUserPrompt.java`, `src/main/java/com/docsync/output/OutputWriteResult.java`, `src/main/java/com/docsync/output/OutputWriter.java`, `src/main/java/com/docsync/output/UserPrompt.java`, and `src/main/java/com/docsync/render/MarkdownRenderer.java`.
- Added report, scan, and security components: `src/main/java/com/docsync/report/JacocoReportReader.java`, `src/main/java/com/docsync/report/SurefireReportReader.java`, `src/main/java/com/docsync/scan/EntryPointResult.java`, `src/main/java/com/docsync/scan/EntryPointScanner.java`, `src/main/java/com/docsync/scan/PomInfo.java`, `src/main/java/com/docsync/scan/PomParser.java`, `src/main/java/com/docsync/scan/ScanOrchestrator.java`, `src/main/java/com/docsync/scan/ScanResult.java`, `src/main/java/com/docsync/security/SecretGuard.java`, and `src/main/java/com/docsync/xml/SecureXmlParser.java`.
- Added CLI and integration tests: `src/test/java/com/docsync/DocSyncCliTest.java` and `src/test/java/com/docsync/DocSyncIntegrationTest.java`.
- Added execution tests and test helpers: `src/test/java/com/docsync/exec/DefaultProcessRunnerTest.java`, `src/test/java/com/docsync/exec/FakeProcessRunner.java`, `src/test/java/com/docsync/exec/TestProcessMain.java`, and `src/test/java/com/docsync/exec/TestRunnerTest.java`.
- Added model, prompt, output, and rendering tests/helpers: `src/test/java/com/docsync/model/DependencyTest.java`, `src/test/java/com/docsync/model/ProjectInfoTest.java`, `src/test/java/com/docsync/model/TestResultTest.java`, `src/test/java/com/docsync/output/ConsoleUserPromptTest.java`, `src/test/java/com/docsync/output/OutputWriterTest.java`, `src/test/java/com/docsync/output/ScriptedUserPrompt.java`, `src/test/java/com/docsync/output/ScriptedUserPromptTest.java`, and `src/test/java/com/docsync/render/MarkdownRendererTest.java`.
- Added report, scanner, orchestration, and security tests: `src/test/java/com/docsync/report/JacocoReportReaderTest.java`, `src/test/java/com/docsync/report/SurefireReportReaderTest.java`, `src/test/java/com/docsync/scan/EntryPointScannerTest.java`, `src/test/java/com/docsync/scan/PomParserTest.java`, `src/test/java/com/docsync/scan/ScanOrchestratorTest.java`, and `src/test/java/com/docsync/security/SecretGuardTest.java`.
- Added this PR description at `docs/pr-description.md`.

## Test Evidence

```text
$ mvn test
[INFO] Tests run: 88, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time: 9.719 s
```

JaCoCo report generation completed during the test run.

## Known Limitations

- Planned tasks T15 (additional error-handling tests) and T16 (additional secret-protection tests) were deferred. Step 7 added the requested empty-folder and fake-`.env` integration scenarios, but does not complete the full planned task scope.
- Per-task approvals during Step 5 were batched because of Copilot quota limits.
- The temporary Maven integration fixture uses a deterministic process fake that creates Surefire and JaCoCo reports; it does not launch a separate Maven build in that fixture.
- There is no deterministic cross-platform integration test for an unreadable directory.
- Coverage is `Not Found` when the scanned project does not provide a JaCoCo report. The tool supports Java/Maven projects only; transitive dependency resolution and continuous watching are out of scope.
- No dedicated CVE/advisory scan was performed.

## Reviewer Checklist

- [ ] Requirements in `docs/requirements.md` are met
- [ ] All tests pass
- [ ] No secrets or `.env` files committed
- [ ] Design and review documents are up to date
- [ ] Code follows the Java rules in the repo
