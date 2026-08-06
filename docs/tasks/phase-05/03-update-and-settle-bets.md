# 5.3 — Update and settle a pending bet

## Context

Only PENDING bets may update or settle; settlement rules are domain-owned.

## Objective

Expose documented update and settlement behavior for the owning authenticated user.

## Acceptance criteria

- [ ] Only an owner can update a PENDING bet.
- [ ] Only an owner can settle a PENDING bet into a documented final status.
- [ ] Resulting profit and return amount match Task 5.1.
- [ ] Invalid lifecycle transitions return the documented conflict/error contract.

## Boundary and negative cases

- [ ] Each final status, missing CASHOUT return, repeated settlement, and cross-user operations.

## Out of scope

- Correction flow, deletion, and event publishing.

## Dependencies

- Task 5.2.

## Expected tests

- Domain/application tests plus persistence and API integration tests.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
