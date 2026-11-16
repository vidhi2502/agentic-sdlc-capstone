# Design Review — US-101: Automated Documentation Sync

> **Stage 3 of 8** · Reviewer: design-review-agent (senior design review)
> **Inputs:** [requirements.md](./requirements.md) (commit `3a3e62d`), [architecture.md](./architecture.md) (commit `ae29a76`)
> **Review areas covered:** requirements coverage, security, error handling, testability, single points of failure, over-engineering, missing edge cases.

## 1. Findings

| ID | Area | Severity | Finding | Decision | Status |
|---|---|---|---|---|---|
| DR-1 | Requirements coverage / error handling | High | FR-3's overwrite prompt is stdin-based; in non-interactive contexts (CI, piped input) the tool hangs or fails. OQ-1 (`--force`) was left open with no defined behavior. | Add `--force` flag. When non-interactive (`System.console() == null`) and the file exists without `--force`, abort with a stderr warning and exit 1. | Resolved |
| DR-2 | Security | High | `TestRunner` executes `mvn test` on the *scanned* project — arbitrary untrusted build plugins and test code run with the user's privileges. Trust boundary was never stated; no escape hatch existed. | Document the trust assumption (tool is for projects the user already trusts/builds locally) and add a `--skip-tests` flag; when skipped, test status = `Not Found`. FR-8 default behavior unchanged. | Resolved |
| DR-3 | Requirements coverage | Medium | Exit-code contract conflicted with requirements: user-declined overwrite was mapped to exit 1 ("error") though FR-3/AC-3 treat declining as normal behavior; exit 2 for "unusable path" was not reconciled with FR-11's "partial file where possible". | Decline ⇒ exit 0 (normal outcome); 1 = partial/warnings (incl. non-interactive abort per DR-1); 2 = unusable path, documented as the physical limit of FR-11's "where possible" (no writable root ⇒ no file). | Resolved |
| DR-4 | Testability | Medium | No test seams: `TestRunner` hard-wired `ProcessBuilder` and `OutputWriter` hard-wired stdin, making both untestable with JUnit 5 (NFR-4). | Introduce injectable `ProcessRunner` and `UserPrompt` interfaces (production impls wrap `ProcessBuilder`/console; tests inject fakes). | Resolved |
| DR-5 | Security | Medium | OQ-2 (token regex set) still open; the proposed "high-entropy literal ≥ 32 chars" rule would cause false positives (checksums, long artifact names), silently corrupting legitimate output with `[REDACTED]`. | Finalize an explicit pattern set: AWS access keys (`AKIA[0-9A-Z]{16}`), JWT-shaped `eyJ…` strings, `(api[_-]?key\|token\|secret\|password)` assignments, PEM private-key headers. Drop the entropy heuristic. Closes OQ-2. | Resolved |
| DR-6 | Error handling / SPOF | Medium | The test run — the single most failure-prone stage — was under-specified: no concrete timeout value and unbounded capture of `mvn` output (memory risk on chatty builds). | 5-minute default timeout; captured output capped at the last 64 KB; timeout/launch failure/missing Maven ⇒ `Not Found` + warning (also closes OQ-3). | Resolved |
| DR-7 | Missing edge cases | Low | `PomParser` did no property interpolation — `<version>${x.version}</version>` would render a raw placeholder; parent-managed dependencies (no `<version>` element) were undefined. | One level of interpolation against the project's own `<properties>`; unresolvable placeholders and absent managed versions render `Not Found`. | Resolved |
| DR-8 | Security / edge cases | Low | `Files.walk` symlink behavior was unspecified — a symlink could escape the scanned folder or create a cycle. | Directory walks do not follow symbolic links. | Resolved |
| DR-9 | Missing edge cases | Low | `target/site/jacoco/jacoco.xml` only exists if the *scanned* project binds the JaCoCo plugin; plain `mvn test` won't produce it, so coverage will usually be absent. | Documented: coverage is expected to be `Not Found` unless the scanned project configures JaCoCo itself. | Resolved |
| DR-10 | Over-engineering / UX | Low | The overwrite prompt ran *after* the expensive `mvn test` run — a user could wait minutes and then decline, wasting the entire run. | Overwrite existence check and prompt moved to the start of the flow, before any scanning or test execution. | Resolved |

**Areas with no findings:** over-engineering beyond DR-5/DR-10 — hand-rolled single-arg parsing, plain `StringBuilder` rendering, and zero runtime dependencies are appropriately minimal; `EntryPointScanner` consulting `SecretGuard` (though it only reads `*.java`) is acceptable defense-in-depth. No additional single points of failure: each collector is isolated by `ScanOrchestrator`, and failure of any one degrades only its own fields.

## 2. Agreed Design Decisions

All decisions were discussed with and accepted by the user on 2026-11-16:

1. **D-1 (from DR-1):** `--force` flag; non-interactive + existing file without `--force` ⇒ abort, warning, exit 1.
2. **D-2 (from DR-2):** Trust boundary documented; `--skip-tests` added (test status `Not Found` when skipped).
3. **D-3 (from DR-3):** Exit codes: `0` = success or user-declined overwrite; `1` = partial/warnings or non-interactive abort; `2` = unusable path (documented limit of FR-11's "where possible").
4. **D-4 (from DR-4):** `ProcessRunner` and `UserPrompt` interfaces added as test seams.
5. **D-5 (from DR-5):** Explicit redaction pattern set finalized; high-entropy heuristic dropped. Closes OQ-2.
6. **D-6 (from DR-6):** 5-minute test-run timeout, 64 KB output cap; missing Maven ⇒ `Not Found` + warning. Closes OQ-3.
7. **D-7–D-10 (from DR-7…DR-10):** Low-severity fixes applied as described in the findings table.

## 3. Changes made to architecture.md

| # | Change | Caused by |
|---|---|---|
| 1 | Header note added pointing to this review; CLI gained `--force` and `--skip-tests` in the component diagram, `DocSyncCli` row, and data flow | DR-1, DR-2 |
| 2 | `DocSyncCli` responsibilities and §6 exit-code list rewritten: decline ⇒ 0, partial/non-interactive abort ⇒ 1, unusable path ⇒ 2 (with FR-11 "where possible" limit documented) | DR-3 |
| 3 | New `ProcessRunner` and `UserPrompt` interface rows in the component table; `TestRunner`/`OutputWriter` rewired through them (diagram, table, data flow, technology-choices rationale) | DR-4 |
| 4 | `SecretGuard` row and §6 "Output redaction" rewritten with the finalized explicit pattern list; entropy rule removed; OQ-2 marked resolved | DR-5 |
| 5 | `TestRunner` row, diagram label, and §6 "Test-run safety" updated with the 5-minute timeout and 64 KB output cap; OQ-3 marked resolved | DR-6 |
| 6 | `PomParser` row updated with one-level `${property}` interpolation and `Not Found` for unresolvable/managed versions | DR-7 |
| 7 | `EntryPointScanner` row and §6 "Exclusion before read" updated: `Files.walk` does not follow symlinks | DR-8 |
| 8 | `JacocoReportReader` row updated with the note that coverage requires the scanned project to configure JaCoCo | DR-9 |
| 9 | Data flow reordered: overwrite existence check + prompt (interactive/non-interactive branches) now precede scanning and test execution; `OutputWriter` row updated accordingly | DR-10, DR-1 |
| 10 | §6 "Test execution trust boundary" paragraph added | DR-2 |
| 11 | "Open items carried to Design Review" section replaced with resolutions of OQ-1, OQ-2, OQ-3 | DR-1, DR-5, DR-6 |
