# 3.3 — Validate JWT at the gateway boundary

## Context

Protected API contracts require a Bearer JWT; auth issuance is Phase 4.

## Objective

Validate valid tokens and reject missing, malformed, expired, or invalid tokens at the Gateway.

## Acceptance criteria

- [ ] Public auth endpoints remain public.
- [ ] Protected routes reject missing and invalid tokens with `401`.
- [ ] A valid token propagates only the needed authenticated identity context.

## Boundary and negative cases

- [ ] Authentication does not grant ownership authorization by itself.

## Out of scope

- User registration, login, roles beyond initial contract, and service-level ownership enforcement.

## Dependencies

- Tasks 3.1, 3.2, and Phase 4.2 contract.

## Expected tests

- Gateway integration tests for public, missing, malformed, expired, and valid JWT cases.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
