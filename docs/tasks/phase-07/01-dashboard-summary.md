# 7.1 — Calculate dashboard summary metrics

## Context

Analytics reads its projections and follows metric rules in `docs/domain.md` and API contract.

## Objective

Return a filtered dashboard summary for the authenticated user.

## Acceptance criteria

- [ ] Total stake, profit, ROI, yield, win rate, average odds, and counts follow documented formulas.
- [ ] PENDING, VOID, and CANCELLED are included/excluded exactly as documented.
- [ ] Empty data returns documented zero values and no cross-user data.

## Boundary and negative cases

- [ ] Zero total stake, negative profit, filters, and decimal scale/rounding.

## Out of scope

- Bankroll evolution and grouped breakdowns.

## Dependencies

- Task 6.3.

## Expected tests

- Calculation unit tests plus analytics API/persistence integration tests.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
