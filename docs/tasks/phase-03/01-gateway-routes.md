# 3.1 — Route public and protected API paths

## Context

The Gateway is the frontend's single entry point; route mappings are in `docs/api-contracts.md`.

## Objective

Route `/auth/**`, `/bets/**`, and `/analytics/**` to their owning services.

## Acceptance criteria

- [ ] Each documented path reaches only its target service.
- [ ] Unknown paths receive the documented gateway error response.
- [ ] The frontend has no direct-service route dependency.

## Boundary and negative cases

- [ ] A route must not accidentally match another service's prefix.

## Out of scope

- JWT validation and CORS policy.

## Dependencies

- Phase 2 skeletons.

## Expected tests

- Gateway integration tests for route success and unmatched paths.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
