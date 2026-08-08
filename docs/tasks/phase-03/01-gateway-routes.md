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

Current status: `DONE`.

| Gate | Decision / evidence | Date / approver |
| --- | --- | --- |
| Specification provided | Approved specification reviewed; requested filename resolves to `01-gateway-routes.md` | 2026-08-07 / user |
| Tests in Red | `GatewayRoutingIntegrationTest`: 7 tests executed, 3 route-success tests failed with HTTP 404 because the expected routes are not implemented; 4 negative/error tests passed. Full Gateway suite: 12 tests, 9 passed and 3 failed for the same routing absence. | 2026-08-07 / test agent |
| Tests approved | Approved by the user | 2026-08-07 / user |
| Approved test correction | The user authorized removing only the obsolete Phase 2 skeleton assertions that prohibited Gateway routes: the route/configuration restriction in `GatewayBoundaryTest` and the business-route `404` test in `GatewayHealthIntegrationTest`. Health, database, persistence, messaging, and all Task 3.1 route/error assertions remain intact. Affected rule: Phase 2 skeleton-only route absence. | 2026-08-07 / user |
| Implementation in progress | Implementation agent authorized after approved Red tests | 2026-08-07 / user |
| Implementation in Green | Added Spring Cloud Gateway routing for `/auth/**`, `/bets/**`, and `/analytics/**`, with configurable service URLs defaulting to ports 8081, 8082, and 8083. Focused routing test passed; full Gateway suite passed with 11 tests; `:services:api-gateway:check` passed including Java 21 validation. | 2026-08-07 / implementation agent |
| Human diff review | Completed by the user | 2026-08-07 / user |
| QA verdict | `APPROVED` — all acceptance criteria, protected-test correction scope, architecture boundaries, security constraints, and test evidence validated. | 2026-08-07 / QA agent |
| QA outcome approved | Human approved the QA outcome. | 2026-08-07 / user |

Focused command:

```text
./gradlew :services:api-gateway:test --tests com.suaposta.gateway.GatewayRoutingIntegrationTest
```

Full module command:

```text
./gradlew :services:api-gateway:test
```

The test agent initially changed only the routing test and this task/roadmap evidence. After the authorized correction, the implementation agent changed the Gateway dependency/configuration and removed only the obsolete Phase 2 route-absence assertions.

## QA audit

```text
VERDICT: APPROVED

Blockers:
- None.

Important issues:
- None.

Non-blocking improvements:
- None for Task 3.1; CORS and JWT remain correctly out of scope.

Evidence:
- Route mappings `/auth/**`, `/bets/**`, and `/analytics/**` target only their documented services.
- Unknown service prefixes and unknown paths are not forwarded and preserve the documented 404 error shape.
- Gateway production code contains no business, persistence, database, or messaging behavior.
- No frontend or unrelated service files were changed.
- `./gradlew :services:api-gateway:check --no-daemon --rerun-tasks` passed, including all 11 Gateway tests and Java 21 validation.
- `git diff --check` passed and the new test file was exposed only with `git add -N`.
```
