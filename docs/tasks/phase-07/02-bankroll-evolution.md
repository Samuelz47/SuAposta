# 7.2 — Expose bankroll evolution

## Context

Bankroll evolves from settled performance bets ordered by `settledAt`.

## Objective

Return ordered cumulative profit/bankroll points for the authenticated user.

## Acceptance criteria

- [ ] Points are ordered by date and use documented settled statuses.
- [ ] Cumulative values calculate correctly with zero-based initial bankroll when applicable.
- [ ] Filters and user isolation are respected.

## Boundary and negative cases

- [ ] No bets, same-date bets, losses, and zero initial bankroll.

## Out of scope

- Deposits, withdrawals, and multiple bankrolls.

## Dependencies

- Task 7.1.

## Expected tests

- Domain/application calculation tests and API integration tests.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
