# 4.1 — Register a user

## Context

Auth owns user identity. See `docs/domain.md` and `docs/api-contracts.md`.

## Objective

Register a new user securely through `POST /auth/register`.

## Acceptance criteria

- [ ] A valid new user receives `201 Created` without a password field.
- [ ] Email uniqueness is enforced with `409 Conflict`.
- [ ] Password is stored only as a hash.
- [ ] Invalid registration input returns the documented validation response.

## Boundary and negative cases

- [ ] Blank/invalid email and password inputs; duplicate email; password exposure.

## Out of scope

- Login, refresh tokens, password reset, and roles administration.

## Dependencies

- Phase 2 Auth skeleton and service database infrastructure.

## Expected tests

- Domain/application tests plus API and persistence integration tests.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
