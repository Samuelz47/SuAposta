# 3.2 — Configure CORS and external error boundaries

## Context

The Gateway owns cross-cutting browser access and must not leak internals.

## Objective

Apply documented local CORS policy and safe gateway-level errors.

## Acceptance criteria

- [ ] Approved frontend origins and methods receive correct CORS headers.
- [ ] Disallowed origins/methods are rejected.
- [ ] Gateway errors contain no internal stack traces or service credentials.

## Boundary and negative cases

- [ ] Preflight behavior is tested separately from an actual protected request.

## Out of scope

- Authentication/authorization decisions.

## Dependencies

- Task 3.1.

## Expected tests

- Gateway HTTP integration tests for preflight, allowed, denied, and safe errors.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
