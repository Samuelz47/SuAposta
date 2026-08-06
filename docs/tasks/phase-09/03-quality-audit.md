# 9.3 — Audit security, error handling, and critical edge cases

## Context

QA audits more than green tests using `docs/definition-of-done.md`.

## Objective

Address documented cross-cutting quality gaps that are within existing scope.

## Acceptance criteria

- [ ] Inputs, authorization, safe errors, secrets, BigDecimal precision, and transaction/idempotency risks are audited.
- [ ] Each correction maps to a demonstrated gap and a test where practical.
- [ ] No feature or architecture expansion occurs without an approved new task.

## Boundary and negative cases

- [ ] Audit findings are categorized as blockers, important issues, or non-blocking improvements.

## Out of scope

- New services, observability stack, and product features.

## Dependencies

- Relevant feature implementation and integration evidence.

## Expected tests

- Focused regression tests for confirmed defects; full relevant suite.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
