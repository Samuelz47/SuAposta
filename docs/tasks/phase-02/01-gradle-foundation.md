# 2.1 — Create the Gradle multi-service build foundation

## Context

Java services must use Java 21 and remain independently buildable.

## Objective

Create the root build conventions and independent service module structure.

## Acceptance criteria

- [ ] Each backend service is a Java 21 buildable module.
- [ ] Tests can run per service without starting unrelated services.
- [ ] No business behavior is implemented.

## Boundary and negative cases

- [ ] A module configured with a non-Java-21 toolchain fails clearly.

## Out of scope

- HTTP endpoints, persistence, and security.

## Dependencies

- Phase 1.

## Expected tests

- Build/toolchain verification and minimal test execution.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
