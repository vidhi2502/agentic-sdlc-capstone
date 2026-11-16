# Requirements — US-101: Automated Documentation Sync

## 1. User Story

> **Source:** `input/user-story.md`
>
> # US-101: Automated Documentation Sync
>
> As a developer, I want a command-line tool that scans a project folder and generates
> a DOCUMENTATION.md file (project name, description, tech stack, dependencies,
> entry points and test status) so that documentation stays in sync with the code.
>
> ## Notes from the product owner
> - Missing information must be shown as "Not Found".
> - Secrets (.env files, keys, tokens) must never appear in the output.
> - Empty folders and missing files must be handled gracefully.

## 2. Clarifying Q&A

| # | Question | User's Answer |
|---|---|---|
| 1 | Which project types must the scanner support? | Java/Maven only |
| 2 | How is the target folder specified? | CLI argument, defaulting to the current working directory |
| 3 | Where is DOCUMENTATION.md written, and what if it already exists? | Root of the scanned folder; ask the user before overwriting an existing file |
| 4 | How much dependency detail is required? | Name + version + scope from the build file |
| 5 | What does "test status" mean? | Run the tests and report pass/fail plus coverage |
| 6 | How must secrets be handled? | Never read or print `.env`, `*.key`, `*.pem` files or values that look like tokens |
| 7 | Is there a performance/size target? | No specific target |
| 8 | What is the error behavior on invalid paths or permission errors? | Generate a partial file with "Not Found" entries, print a warning, and exit with a non-zero code |

## 3. Functional Requirements

| ID | Requirement |
|---|---|
| FR-1 | The tool SHALL accept an optional folder path as a command-line argument; when no argument is given it SHALL scan the current working directory. |
| FR-2 | The tool SHALL generate a `DOCUMENTATION.md` file at the root of the scanned folder containing exactly these sections: project name, description, tech stack, dependencies, entry points, and test status. |
| FR-3 | If `DOCUMENTATION.md` already exists at the target location, the tool SHALL prompt the user for confirmation before overwriting it, and SHALL abort without writing if the user declines. |
| FR-4 | The tool SHALL extract the project name and description from `pom.xml` (`<name>`/`<artifactId>` and `<description>`). |
| FR-5 | The tool SHALL report the tech stack by detecting the Java version and build tool from `pom.xml` (e.g., `maven.compiler.source` / `java.version` properties). |
| FR-6 | The tool SHALL list each dependency declared in `pom.xml` with its group/artifact name, version, and scope (defaulting to `compile` when scope is omitted). |
| FR-7 | The tool SHALL identify entry points by locating classes containing a `public static void main(String[] args)` method under `src/main/java`. |
| FR-8 | The tool SHALL run the project's tests via Maven (`mvn test`) and report the result as pass/fail with the test counts, plus code coverage when a JaCoCo report is available. |
| FR-9 | Any item of information that cannot be determined SHALL be rendered in the output as the literal text `Not Found`. |
| FR-10 | The tool SHALL handle empty folders, missing `pom.xml`, and missing source/test directories gracefully: it SHALL still produce a `DOCUMENTATION.md` with `Not Found` entries rather than crashing. |
| FR-11 | On an invalid path, unreadable folder, or permission error, the tool SHALL write a partial `DOCUMENTATION.md` (with `Not Found` for unavailable data), print a warning describing the problem to stderr, and exit with a non-zero exit code. |

## 4. Non-Functional Requirements

| ID | Category | Requirement |
|---|---|---|
| NFR-1 | Security | The tool SHALL never open, read, or print the contents of `.env` files, `*.key` files, or `*.pem` files, and SHALL exclude them from all output. |
| NFR-2 | Security | The tool SHALL NOT include values that look like tokens or secrets (e.g., strings matching common API-key/token patterns) in `DOCUMENTATION.md`; any such value encountered SHALL be omitted or redacted. |
| NFR-3 | Reliability | The tool SHALL never terminate with an unhandled exception/stack trace for any folder input; all error paths SHALL produce a clear message and a defined exit code (0 = success, non-zero = error per FR-11). |
| NFR-4 | Maintainability | The tool SHALL be implemented in Java 17, built with Maven, and covered by JUnit 5 unit tests with coverage measured via JaCoCo. |
| NFR-5 | Performance | No specific performance target was given; the scan SHALL avoid reading file contents that are not needed to produce the output (except for running tests per FR-8). |

## 5. Acceptance Criteria

| # | Given / When / Then | Covers |
|---|---|---|
| AC-1 | **Given** a valid Maven project folder passed as a CLI argument, **when** the tool runs, **then** `DOCUMENTATION.md` is created at the folder root with all six sections populated from `pom.xml` and the source tree. | FR-1, FR-2, FR-4, FR-5, FR-6, FR-7 |
| AC-2 | **Given** no CLI argument, **when** the tool runs, **then** it scans the current working directory. | FR-1 |
| AC-3 | **Given** `DOCUMENTATION.md` already exists in the target folder, **when** the tool runs, **then** it asks for confirmation, and only overwrites after the user confirms; if the user declines, the existing file is unchanged. | FR-3 |
| AC-4 | **Given** a Maven project with tests, **when** the tool runs, **then** the test status section shows pass/fail with test counts, and coverage percentage when a JaCoCo report exists. | FR-8 |
| AC-5 | **Given** a folder with no `pom.xml` (or an empty folder), **when** the tool runs, **then** `DOCUMENTATION.md` is still produced and every undeterminable field shows `Not Found`, with no crash. | FR-9, FR-10, NFR-3 |
| AC-6 | **Given** a project containing `.env`, `*.key`, or `*.pem` files, **when** the tool runs, **then** none of those files' names appear with contents in the output, their contents are never read, and no token-like value appears in `DOCUMENTATION.md`. | NFR-1, NFR-2 |
| AC-7 | **Given** an invalid path or a folder with permission errors, **when** the tool runs, **then** a partial `DOCUMENTATION.md` is written where possible, a warning is printed to stderr, and the exit code is non-zero. | FR-11, NFR-3 |

## 6. Assumptions, Out of Scope, Open Questions

### Assumptions
- A-1: "Project name" prefers `<name>` in `pom.xml`, falling back to `<artifactId>`; both missing ⇒ `Not Found`.
- A-2: The overwrite confirmation (FR-3) is an interactive stdin prompt; non-interactive use was not discussed.
- A-3: Maven is available on the PATH of the machine where the tool runs (needed for FR-8).
- A-4: Dependency scope defaults to `compile` when not declared, per Maven conventions.

### Out of Scope
- OOS-1: Support for non-Maven project types (Node.js, Python, Gradle, etc.).
- OOS-2: Resolving or listing transitive dependencies.
- OOS-3: Specific performance/size guarantees (none requested).
- OOS-4: Watching the folder for changes / continuous sync; the tool is a one-shot CLI run.

### Open Questions
- OQ-1: Should FR-3's overwrite prompt support a non-interactive flag (e.g., `--force`) for CI usage?
- OQ-2: What exact regex/patterns define a "value that looks like a token" for NFR-2?
- OQ-3: If `mvn test` itself fails to start (Maven not installed), should test status be `Not Found` or an error per FR-11?
