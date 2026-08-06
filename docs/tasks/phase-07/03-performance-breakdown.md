# 7.3 — Expose filtered performance breakdowns

## Context

Performance may be grouped by documented dimensions from Analytics projections.

## Objective

Return an authenticated user's grouped metrics for a valid `groupBy`.

## Acceptance criteria

- [ ] Supported groupings return correct aggregates.
- [ ] Invalid groupings return `400`.
- [ ] Filters and user isolation are maintained.

## Boundary and negative cases

- [ ] Empty result, null dimension, invalid grouping, decimal results.

## Out of scope

- Exports, scheduled reports, and pre-aggregated tables.

## Dependencies

- Task 7.1.

## Expected tests

- Calculation tests and API/persistence integration tests.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
