# 5.2 — Create and retrieve a user's pending bets

## Context

Betting owns its lifecycle and `POST /bets`, `GET /bets`, and `GET /bets/{id}` contracts are documented.

## Objective

Create pending bets and retrieve only the authenticated user's bets.

## Acceptance criteria

- [ ] Valid creation persists a PENDING bet and returns `201`.
- [ ] Authenticated identity—not request input—determines `userId`.
- [ ] Listing supports documented optional filters/pagination and user isolation.
- [ ] Reading another user's bet does not expose it.

## Boundary and negative cases

- [ ] Invalid stake/odds, empty result, missing ID, unauthorized access, and cross-user access.

## Out of scope

- Update, settlement, and RabbitMQ events.

## Dependencies

- Task 5.1, authentication identity contract, and betting migration.

## Expected tests

- Domain/application tests, repository Testcontainers tests, and RestAssured API tests.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
