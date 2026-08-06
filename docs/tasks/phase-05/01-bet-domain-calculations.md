# 5.1 — Establish Bet value objects and settlement calculations

## Context

Domain rules in `docs/domain.md` define positive stake, odds above one, lifecycle, profit, return, BigDecimal, and scale.

## Objective

Implement pure Bet domain behavior and calculations before persistence or HTTP.

## Acceptance criteria

- [ ] Stake must be positive and odds greater than one.
- [ ] WON profit is `stake × odds − stake`; LOST profit is `−stake`.
- [ ] VOID and CANCELLED profit are zero; CASHOUT uses return minus stake.
- [ ] Only PENDING may settle; a settled bet cannot settle again.
- [ ] Money and odds use `BigDecimal` and the documented domain scale.

## Boundary and negative cases

- [ ] Zero/negative stake, odds equal to/below one, scale/rounding, each settlement status, and repeated settlement.

## Out of scope

- Repositories, APIs, events, and analytics aggregation.

## Dependencies

- Phase 2 Betting skeleton; confirm domain scale in `docs/domain.md` before test approval.

## Expected tests

- Domain unit tests in Red for all criteria and boundaries; no mocks.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
