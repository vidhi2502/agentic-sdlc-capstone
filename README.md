# agentic-sdlc-capstone

A command-line tool that scans a Maven project folder and generates a `DOCUMENTATION.md`
with the project name, description, tech stack, dependencies, entry points and test status.
This repository was built end to end with an **agentic SDLC pipeline** using GitHub Copilot.

## Build
```
mvn package
```

## Usage
```
java -jar target/<jar-name>.jar [folder] [--force] [--skip-tests]
```
- `folder`: project to scan (default: current directory)
- `--force`: overwrite an existing DOCUMENTATION.md without asking
- `--skip-tests`: do not run `mvn test`; test status is shown as "Not Found"

Check the exact jar name in the `target/` folder after building.

## Exit codes
| Code | Meaning |
|---|---|
| 0 | Success, or the user declined to overwrite |
| 1 | Partial result with warnings, or non-interactive run blocked from overwriting |
| 2 | Unusable folder path |

## Behaviour notes
- Missing information is always shown as `Not Found`.
- `.env`, `*.key` and `*.pem` files are never read, and token-like strings are replaced with `[REDACTED]`.
- Test status comes from running `mvn test` in the scanned project. Only scan projects you trust.
- Code coverage appears only if a JaCoCo report already exists.

## The agentic SDLC pipeline
Each stage is a custom Copilot agent in `.github/agents/`, coordinated by an orchestrator.

| Stage | Agent | Artifact |
|---|---|---|
| 1 | requirements-agent | docs/requirements.md |
| 2 | architecture-agent | docs/architecture.md |
| 3 | design-review-agent | docs/design-review.md |
| 4 | planning-agent | docs/impl-plan.md |
| 5 | implementation-agent | src/main/java, src/test/java |
| 6 | code-review-agent | docs/code-review.md |
| 7 | verification-agent | docs/verification-report.md |
| 8 | pr-agent | Pull Request |

Supporting pieces: global instructions (`.github/copilot-instructions.md`), Java rules
(`.github/instructions/`), 4 skills (`.github/skills/`), PowerShell hooks for an audit log and a
safety guard (`.github/hooks/`), and prompt shortcuts (`.github/prompts/`).
Pipeline progress is tracked in `docs/pipeline-state.md` and the audit trail in `docs/pipeline-log.md`.