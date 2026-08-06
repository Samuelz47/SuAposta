# 6.2 — Publish betting lifecycle events after persistence

## Context

Betting publishes `BET_CREATED`, `BET_UPDATED`, and `BET_SETTLED` only after successful database changes.

## Objective

Publish the documented event for each completed lifecycle operation.

## Acceptance criteria

- [ ] Creation, update, and settlement publish matching documented events.
- [ ] An operation that fails before persistence publishes no event.
- [ ] Published payload and routing key match `docs/events.md`.

## Boundary and negative cases

- [ ] Persistence failure, serialization failure, and each lifecycle event are covered.

## Out of scope

- Transactional outbox, retry policy, DLQ, and analytics consumption.

## Dependencies

- Tasks 5.2, 5.3, and 6.1.

## Expected tests

- Application unit tests at publisher port and messaging integration tests.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
