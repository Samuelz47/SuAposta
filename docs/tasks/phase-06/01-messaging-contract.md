# 6.1 — Configure documented exchange, queue, and event envelope

## Context

`docs/events.md` is the source of truth for topology, routing keys, and envelope fields.

## Objective

Configure the initial RabbitMQ topology and serializable version-one envelope.

## Acceptance criteria

- [ ] Exchange `betting.events`, queue, bindings, and routing keys match documentation.
- [ ] Envelope includes eventId, eventType, occurredAt, version, producer, and payload.
- [ ] Serialization preserves documented fields and BigDecimal values.

## Boundary and negative cases

- [ ] Invalid/missing envelope fields are rejected or safely handled as documented.

## Out of scope

- Publishing lifecycle events or analytics consumption.

## Dependencies

- Phase 1 RabbitMQ and Phase 2 service skeletons.

## Expected tests

- Serialization tests and RabbitMQ/Testcontainers topology integration tests.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
