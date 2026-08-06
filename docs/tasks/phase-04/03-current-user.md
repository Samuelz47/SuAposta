# 4.3 — Identify the authenticated user

## Context

`GET /auth/me` exposes safe identity data for the current JWT subject.

## Objective

Return the authenticated user's documented identity response.

## Acceptance criteria

- [ ] A valid JWT returns the corresponding user without credentials.
- [ ] Missing or invalid JWT returns `401`.
- [ ] A user cannot obtain another user's identity by request input.

## Boundary and negative cases

- [ ] Deleted/nonexistent JWT subject behavior is defined and tested.

## Out of scope

- Profile editing and roles administration.

## Dependencies

- Task 4.2 and Gateway JWT contract.

## Expected tests

- Application unit and API integration tests for identity and token failures.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
