# 9.1 — Add integration coverage for persistence and APIs

## Context

Feature tasks already require focused tests; this task closes intentionally documented integration gaps.

## Objective

Add missing high-value PostgreSQL/Flyway and HTTP contract integration coverage.

## Acceptance criteria

- [ ] Gaps are identified from existing task evidence, not guessed from coverage percentage.
- [ ] Important persistence constraints/mappings and HTTP failure contracts are exercised realistically.
- [ ] Tests use Testcontainers/RestAssured where the integration behavior matters.

## Boundary and negative cases

- [ ] Existing unit tests are not substituted for required integration tests.

## Out of scope

- New product behavior or broad refactors.

## Dependencies

- Relevant feature tasks completed.

## Expected tests

- PostgreSQL Testcontainers and RestAssured integration tests.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
