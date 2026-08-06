# 1.1 — Define Docker Compose configuration and environment contract

## Context

Local infrastructure must follow `docs/architecture.md` and expose no hardcoded secrets.

## Objective

Define Compose services, ports, networks, volumes, and environment-variable contract before provisioning.

## Acceptance criteria

- [ ] Compose declares PostgreSQL and RabbitMQ with documented local ports.
- [ ] Credentials and database configuration come from safe environment configuration.
- [ ] Persistent data uses named volumes.

## Boundary and negative cases

- [ ] Default local configuration must not contain production credentials.

## Out of scope

- Database initialization and RabbitMQ topology.

## Dependencies

- Phase 0 approved.

## Expected tests

- Configuration inspection and `docker compose config`; document any automated smoke-test limitation.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
