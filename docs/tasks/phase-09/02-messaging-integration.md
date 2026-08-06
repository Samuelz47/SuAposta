# 9.2 — Add messaging integration coverage

## Context

Events must follow `docs/events.md` and Analytics consumption must be idempotent.

## Objective

Close documented gaps in end-to-end message routing, serialization, and duplicate handling.

## Acceptance criteria

- [ ] Published events reach the documented queue and update projection once.
- [ ] Duplicate delivery is proven idempotent.
- [ ] Event envelope and payload contracts are validated.

## Boundary and negative cases

- [ ] Bad/unknown messages have explicit, tested handling.

## Out of scope

- Outbox, DLQ, replay, and advanced retry policies.

## Dependencies

- Tasks 6.1–6.3.

## Expected tests

- RabbitMQ and PostgreSQL Testcontainers integration tests.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
