# Implementation Plan — US-101: Automated Documentation Sync

> **Stage 4 of 8** · planning-agent
> **Inputs:** [requirements.md](./requirements.md) (commit `3a3e62d`), [architecture.md](./architecture.md) (commit `ae29a76`, revised in Stage 3), [design-review.md](./design-review.md) (commit `3f27ec0`)
> Every task includes writing its own JUnit 5 unit tests unless stated otherwise. No production code is written in this stage.

## 1. Task Table

| Task ID | Description | Priority | Depends On | Requirement IDs | Status |
|---|---|---|---|---|---|
| T1 | Maven project setup: `pom.xml` with Java 17 (`maven.compiler.source/target`), JUnit 5 dependency, Surefire plugin, JaCoCo plugin bound to `test`, and jar manifest with main class `DocSyncCli`; standard `src/main/java` / `src/test/java` layout; `.gitignore` for `target/` | P0 | — | NFR-4 | Done |
| T2 | `ProjectInfo` immutable data model plus `Dependency` and `TestResult` records; every field carries an explicit `Not Found` representation (e.g. `Optional`-style accessors with `Not Found` fallback) | P0 | T1 | FR-2, FR-9 | Done |
| T3 | `SecretGuard`: (a) path exclusion for `.env`, `*.key`, `*.pem` (never opened/read); (b) output redaction with the finalized pattern set (AWS `AKIA[0-9A-Z]{16}`, JWT `eyJ…` triplets, `(api[_-]?key\|token\|secret\|password)` assignments, PEM private-key headers) replacing matches with `[REDACTED]` (DR-5) | P0 | T1 | NFR-1, NFR-2 | Done |
| T4 | `PomParser`: JDK DOM parser with `FEATURE_SECURE_PROCESSING` + XXE disabled; extracts name (`<name>` → `<artifactId>` fallback), description, Java version (`maven.compiler.source`/`java.version`), build tool, dependencies (group/artifact, version, scope defaulting `compile`); one level of `${property}` interpolation; unresolvable/parent-managed versions ⇒ `Not Found` (DR-7); missing/unparseable `pom.xml` ⇒ `Not Found` fields | P0 | T1, T2 | FR-4, FR-5, FR-6, FR-9 | Done |
| T5 | `EntryPointScanner`: walks `src/main/java` via `Files.walk` **without following symlinks** (DR-8), reads only `*.java` files, detects `public static void main(String[] args)` via line-level pattern, consults `SecretGuard.isExcluded` before any read; missing source dir ⇒ `Not Found` | P0 | T2, T3 | FR-7, NFR-1, NFR-5 | Done |
| T6 | `ProcessRunner` interface (test seam, DR-4) + production implementation wrapping `ProcessBuilder` with argument-list launch (no shell interpolation), configurable timeout, and captured output capped at the last 64 KB; plus a scripted fake for tests | P1 | T1 | FR-8, NFR-4 | Done |
| T7 | `SurefireReportReader`: hardened DOM parse of `target/surefire-reports/*.xml` for total/passed/failed/skipped counts and derived pass/fail status; missing/invalid reports ⇒ `Not Found` | P1 | T1, T2 | FR-8, FR-9 | Done |
| T8 | `JacocoReportReader`: hardened DOM parse of `target/site/jacoco/jacoco.xml` for line coverage %; absent report ⇒ coverage `Not Found` (DR-9) | P1 | T1, T2 | FR-8, FR-9 | Not Started |
| T9 | `TestRunner`: runs `mvn test` in the target folder via `ProcessRunner` with a 5-minute timeout (DR-6); skipped entirely under `--skip-tests` ⇒ test status `Not Found` (DR-2); missing Maven / launch failure / timeout ⇒ `Not Found` + stderr warning (OQ-3); delegates parsing to T7/T8 readers; unit-tested with the fake `ProcessRunner` | P1 | T2, T6, T7, T8 | FR-8 | Not Started |
| T10 | `MarkdownRenderer`: renders `ProjectInfo` into exactly the six required sections (name, description, tech stack, dependencies, entry points, test status), emitting literal `Not Found` for missing data and passing all output through `SecretGuard.redact` | P0 | T2, T3 | FR-2, FR-9, NFR-2 | Not Started |
| T11 | `UserPrompt` interface (test seam, DR-4) + console implementation reading the stdin yes/no confirmation; plus a scripted fake for tests | P1 | T1 | FR-3, NFR-4 | Not Started |
| T12 | `OutputWriter`: writes `DOCUMENTATION.md` at the scanned folder root; overwrite handling per DR-1/DR-10 — `--force` ⇒ overwrite silently; interactive (`System.console() != null`) ⇒ confirm via `UserPrompt`, abort unchanged on decline; non-interactive without `--force` ⇒ abort with warning; exposes the existence check for the CLI's early prompt | P0 | T10, T11 | FR-2, FR-3 | Not Started |
| T13 | `ScanOrchestrator`: invokes PomParser → EntryPointScanner → TestRunner in sequence, catches each collector's exceptions independently so one failure degrades only its own fields to `Not Found` (FR-10), accumulates stderr warnings, always returns a `ProjectInfo` | P0 | T2, T4, T5, T9 | FR-9, FR-10, FR-11, NFR-3 | Not Started |
| T14 | `DocSyncCli` (`main`): hand-rolled parsing of optional folder arg (default CWD) plus `--force` / `--skip-tests`; path validation; **early** overwrite existence check + prompt before any scanning (DR-10); top-level catch so no stack trace ever escapes; exit codes 0 = success or declined overwrite, 1 = partial/warnings or non-interactive abort, 2 = unusable path (DR-3) | P0 | T12, T13 | FR-1, FR-3, FR-11, NFR-3 | Not Started |
| T15 | Error-handling integration tests on temp folders: empty folder, missing `pom.xml`, missing `src`/`test` dirs ⇒ file still produced with `Not Found` (AC-5); invalid path / unreadable folder ⇒ partial file where possible, stderr warning, non-zero exit (AC-7); exit-code contract 0/1/2 verified end-to-end | P1 | T14 | FR-9, FR-10, FR-11, NFR-3 | Not Started |
| T16 | Secret-protection tests: fixture project containing `.env`, `*.key`, `*.pem` and token-like strings ⇒ contents never read (verify via `SecretGuard`/scanner behavior), nothing secret in `DOCUMENTATION.md`, token patterns rendered as `[REDACTED]` (AC-6) | P1 | T14 | NFR-1, NFR-2 | Not Started |
| T17 | README: usage (`docsync [folder] [--force] [--skip-tests]`), exit-code table, trust-boundary note for `mvn test` (DR-2), JaCoCo-coverage caveat (DR-9), build/run instructions | P2 | T14 | NFR-4 | Not Started |

## 2. Requirements Coverage Check

| Requirement | Covered by |
|---|---|
| FR-1 | T14 |
| FR-2 | T2, T10, T12 |
| FR-3 | T11, T12, T14 |
| FR-4, FR-5, FR-6 | T4 |
| FR-7 | T5 |
| FR-8 | T6, T7, T8, T9 |
| FR-9 | T2, T4, T7, T8, T10, T13, T15 |
| FR-10 | T13, T15 |
| FR-11 | T13, T14, T15 |
| NFR-1 | T3, T5, T16 |
| NFR-2 | T3, T10, T16 |
| NFR-3 | T13, T14, T15 |
| NFR-4 | T1, T6, T11, T17 |
| NFR-5 | T5 |

All functional and non-functional requirements are covered by at least one task. **No uncovered requirements.**

## 3. Blocked Tasks

- T2 is blocked by T1
- T3 is blocked by T1
- T4 is blocked by T1, T2
- T5 is blocked by T2, T3
- T6 is blocked by T1
- T7 is blocked by T1, T2
- T8 is blocked by T1, T2
- T9 is blocked by T2, T6, T7, T8
- T10 is blocked by T2, T3
- T11 is blocked by T1
- T12 is blocked by T10, T11
- T13 is blocked by T2, T4, T5, T9
- T14 is blocked by T12, T13
- T15 is blocked by T14
- T16 is blocked by T14
- T17 is blocked by T14

Only T1 can start immediately.

## 4. Suggested Order of Execution

1. **Foundation:** T1 → T2, T3 (model and security policy unlock everything else).
2. **Collectors:** T4 and T5 (pom + entry points), then the test pipeline T6 → T7, T8 → T9.
3. **Output path:** T10 → T11 → T12 (renderer, prompt seam, writer).
4. **Assembly:** T13 (orchestrator) → T14 (CLI wiring, exit codes).
5. **Hardening & docs:** T15 and T16 (integration + secret tests, can run in parallel), finally T17 (README).

After T1, the pairs {T2, T3}, {T6, T11} and later {T4, T5}, {T7, T8}, {T15, T16} are mutually independent and may be implemented in any order or in parallel.
