# 3.2 — Configure CORS and external error boundaries

## Context

The Gateway owns cross-cutting browser access and must not leak internals.

## Objective

Apply documented local CORS policy and safe gateway-level errors.

## Acceptance criteria

- [ ] Approved frontend origins and methods receive correct CORS headers.
- [ ] Disallowed origins/methods are rejected.
- [ ] Gateway errors contain no internal stack traces or service credentials.

## Boundary and negative cases

- [ ] Preflight behavior is tested separately from an actual protected request.

## Out of scope

- Authentication/authorization decisions.

## Dependencies

- Task 3.1.

## Expected tests

- Gateway HTTP integration tests for preflight, allowed, denied, and safe errors.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Current status: `DONE`.

| Current status | Pending gate |
| --- | --- |
| DONE | Independent QA audit completed and human approved. |

| Gate | Decision / evidence | Date / approver |
| --- | --- | --- |
| Specification provided | Task specification and required project documents reviewed. No implementation proposal was inspected. | 2026-08-07 / test agent |
| Tests in Red | `GatewayCorsAndErrorIntegrationTest` now contains 12 tests. Focused command `./gradlew :services:api-gateway:test --tests com.suaposta.gateway.GatewayCorsAndErrorIntegrationTest --no-daemon` compiled production and test sources successfully, then failed 9 tests: approved-origin real request had no CORS headers; all 4 approved-method preflights returned 403; the contract-derived method-list preflight returned 403; disallowed origin was forwarded; both JSON and plain-text upstream errors leaked unsafe content. Three tests passed because the current framework already rejects the DELETE/TRACE preflights and preserves the documented unknown-path 404 shape. Full command `./gradlew :services:api-gateway:test --no-daemon` executed 23 tests: 14 passed and the same 9 failures were exclusively in Task 3.2; all 11 existing Task 3.1 tests remained passing. | 2026-08-07 / test agent |
| Tests approved | Human approved the 12 Task 3.2 tests; the approved tests are frozen and must not be weakened, removed, or altered during implementation. | 2026-08-07 / user |
| Implementation in Green | Added the documented local CORS policy and a global upstream boundary that filters successful and error responses to external headers only, preserving `Content-Type`, applicable CORS headers, and `Vary`. Upstream connection failures now return the same safe JSON error contract with a `502 Bad Gateway` status. The response flow consumes and releases the upstream body exactly once, including empty bodies, and emits the real request path. Focused Task 3.2 tests passed with 14 tests; the complete API Gateway suite passed with 25 tests; the relevant API Gateway check passed, including Java 21 verification. | 2026-08-07 / implementation agent |
| Human diff review | Approved by the human after implementation review. No QA audit was performed in this implementation step. | 2026-08-07 / user |
| QA verdict | `APPROVED WITH RESERVATIONS` — all current Task 3.2 acceptance criteria and security-audit tests passed. Reservation: the global fallback catches any downstream exception and maps it to `502`; future filters should classify exceptions to avoid masking out-of-scope behavior. | 2026-08-07 / QA agent |
| QA outcome approved | Human approved the QA outcome. | 2026-08-07 / user |

### Test assumptions and specification ambiguities

- The docs do not define the exact approved frontend origin; tests use the Angular local-development origin `http://localhost:4200`.
- The docs do not define a standalone CORS section; the method-list test derives the expected business methods from the HTTP endpoints documented in `docs/api-contracts.md` (`GET`, `POST`, `PUT`, and `PATCH`). `OPTIONS` is exercised only as the preflight mechanism, while `Authorization` and `Content-Type` are derived from the documented protected-API/JSON requirements.
- The docs do not define the exact status/message for a rejected CORS request or an upstream failure; tests require rejection for disallowed origin/method and a stable JSON error shape with a 5xx status, while allowing the implementation to choose the precise gateway 5xx status and generic message.

### Approved-test changes

None. Existing Task 3.1 tests were not changed.

### Post-approval security-audit test additions

At the user's request, two additional tests were appended without altering the 12 previously approved Task 3.2 tests:

- `should_strip_sensitive_upstream_headers_from_successful_responses_and_preserve_external_headers` verifies that successful upstream responses do not expose `Authorization`, `Set-Cookie`, or internal/custom service headers, while preserving `Content-Type`, `Access-Control-Allow-Origin`, and `Vary: Origin`.
- `should_return_a_safe_gateway_error_when_upstream_connection_is_unavailable` simulates an upstream connection closed before a response and requires the documented JSON error fields, CORS origin, and no exception, connection, internal-address, or service details.

Focused command:

```text
./gradlew :services:api-gateway:test --tests com.suaposta.gateway.GatewayCorsAndErrorIntegrationTest --no-daemon
```

Result before implementation: 14 tests executed, 12 passed, and the 2 new audit tests remained Red. The successful-response test failed because all five upstream-sensitive headers were propagated. The unavailable-upstream test failed because the response omitted the required `message` field, although it did not expose the checked internal connection details.

Full Gateway command:

```text
./gradlew :services:api-gateway:test --no-daemon
```

Result after implementation: 25 tests executed, all passed, with the 2 new audit tests green. No approved test was altered, removed, disabled, or weakened.

Relevant check:

```text
./gradlew :services:api-gateway:check --no-daemon
```

Result: passed, including the complete API Gateway test suite and `verifyJava21`.
