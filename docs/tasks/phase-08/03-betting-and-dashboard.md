# 8.3 — Implement betting and dashboard flows

## Context

Betting and Analytics APIs are consumed only through the Gateway.

## Objective

Expose initial bet listing/creation/settlement and dashboard metrics in the UI.

## Acceptance criteria

- [ ] User can list, create, and settle own bets through documented contracts.
- [ ] Dashboard displays returned summary and handles empty/error states.
- [ ] Decimal values and statuses are presented without client-side business recalculation.

## Boundary and negative cases

- [ ] Invalid form input, API error, no data, and unauthorized session behavior.

## Out of scope

- Advanced design system, offline mode, and websocket updates.

## Dependencies

- Tasks 5.2, 5.3, 7.1–7.3, and 8.2.

## Expected tests

- Angular component and HTTP-service tests for essential user-visible behavior.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
