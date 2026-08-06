# 1.2 — Provision PostgreSQL with service-owned databases

## Context

One PostgreSQL container must host `auth_db`, `betting_db`, and `analytics_db` without cross-service ownership.

## Objective

Provision and verify the three initial development databases.

## Acceptance criteria

- [ ] PostgreSQL starts from the documented Compose configuration.
- [ ] `auth_db`, `betting_db`, and `analytics_db` exist.
- [ ] Initialization is repeatable and does not require manual SQL.

## Boundary and negative cases

- [ ] Recreating a local environment does not accidentally use a production connection.

## Out of scope

- Service schemas and Flyway migrations.

## Dependencies

- Task 1.1.

## Expected tests

- Container smoke test plus database existence checks.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
