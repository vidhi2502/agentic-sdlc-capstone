# US-101: Automated Documentation Sync

As a developer, I want a command-line tool that scans a project folder and generates
a DOCUMENTATION.md file (project name, description, tech stack, dependencies,
entry points and test status) so that documentation stays in sync with the code.

## Notes from the product owner
- Missing information must be shown as "Not Found".
- Secrets (.env files, keys, tokens) must never appear in the output.
- Empty folders and missing files must be handled gracefully.