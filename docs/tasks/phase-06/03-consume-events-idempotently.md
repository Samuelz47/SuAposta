# 6.3 — Consume events idempotently into analytics projections

## Context

Analytics consumes betting events into its own database and tracks processed event IDs.

## Objective

Build or update analytics projections once per valid event.

## Acceptance criteria

- [ ] Each documented event produces the correct projection change.
- [ ] Repeated `eventId` does not apply a second change.
- [ ] Analytics never writes Betting's database.

## Boundary and negative cases

- [ ] Duplicate, unknown, malformed, and out-of-order event decisions are documented and tested.

## Out of scope

- Replay, DLQ, advanced retries, and dashboard endpoints.

## Dependencies

- Tasks 6.1 and 6.2; analytics migration.

## Expected tests

- Consumer unit tests and RabbitMQ/PostgreSQL Testcontainers integration tests.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
