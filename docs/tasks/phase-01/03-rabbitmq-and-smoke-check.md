# 1.3 — Provision RabbitMQ and validate local infrastructure

## Context

RabbitMQ supports the asynchronous Betting-to-Analytics flow documented in `docs/events.md`.

## Objective

Start RabbitMQ, expose its management UI locally, and validate all Phase 1 services together.

## Acceptance criteria

- [ ] RabbitMQ AMQP and management ports match the documented contract.
- [ ] PostgreSQL and RabbitMQ start through one documented command.
- [ ] A smoke check verifies both services are reachable.

## Boundary and negative cases

- [ ] Service startup failure is visible and not masked by a successful Compose exit.

## Out of scope

- Exchange, queue, or application event configuration.

## Dependencies

- Tasks 1.1 and 1.2.

## Expected tests

- Container smoke tests and documented commands.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
