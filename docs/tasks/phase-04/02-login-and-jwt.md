# 4.2 — Authenticate a user and issue a JWT

## Context

Only registered users with valid credentials can receive a JWT.

## Objective

Implement `POST /auth/login` according to the documented contract.

## Acceptance criteria

- [ ] Valid credentials return a signed access token and documented user response.
- [ ] Invalid credentials return `401` without revealing which field failed.
- [ ] Passwords and password hashes never appear in the response or JWT claims.

## Boundary and negative cases

- [ ] Missing, malformed, and incorrect credentials are rejected consistently.

## Out of scope

- Refresh tokens, password reset, OAuth, and social login.

## Dependencies

- Task 4.1.

## Expected tests

- Application unit tests, JWT contract tests, and API integration tests.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
