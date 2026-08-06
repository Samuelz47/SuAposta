# 8.2 — Implement registration and login flow

## Context

Auth API contracts and Gateway JWT behavior are already defined.

## Objective

Allow a user to register and authenticate through the Gateway.

## Acceptance criteria

- [ ] Forms validate required fields and show safe API errors.
- [ ] Successful login stores/sends JWT through the configured interceptor.
- [ ] Guards prevent protected route access without a valid session.

## Boundary and negative cases

- [ ] Invalid credentials, failed registration, expired/missing token, and loading/error states.

## Out of scope

- Password reset, OAuth, and profile editing.

## Dependencies

- Tasks 4.1–4.3 and 8.1.

## Expected tests

- Angular component, form, interceptor, guard, and API-service tests.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
