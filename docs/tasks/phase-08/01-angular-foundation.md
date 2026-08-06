# 8.1 — Bootstrap Angular app, layout, and API boundary

## Context

The frontend calls only the Gateway and follows the feature structure in `docs/architecture.md`.

## Objective

Create the Angular 18 baseline, basic layout, routes, and gateway client boundary.

## Acceptance criteria

- [ ] App builds and serves with documented local configuration.
- [ ] Feature/core/shared structure exists without premature state management.
- [ ] HTTP clients target only the Gateway base URL.

## Boundary and negative cases

- [ ] No direct request to an internal service URL is possible through application services.

## Out of scope

- Login behavior, betting UI, and dashboard UI.

## Dependencies

- Gateway route contract.

## Expected tests

- Focused Angular service/component tests for route and HTTP boundary behavior.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
