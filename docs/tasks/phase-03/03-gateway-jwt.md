# 3.3 — Validate JWT at the gateway boundary

## Context

Protected API contracts require a Bearer JWT; auth issuance is Phase 4.

## Objective

Validate valid tokens and reject missing, malformed, expired, or invalid tokens at the Gateway.

## Acceptance criteria

- [ ] Public auth endpoints remain public.
- [ ] Protected routes reject missing and invalid tokens with `401`.
- [ ] A valid token propagates only the needed authenticated identity context.

## Boundary and negative cases

- [ ] Authentication does not grant ownership authorization by itself.

## Out of scope

- User registration, login, roles beyond initial contract, and service-level ownership enforcement.

## Dependencies

- Tasks 3.1, 3.2, and Phase 4.2 contract.

## Expected tests

- Gateway integration tests for public, missing, malformed, expired, and valid JWT cases.
- JWT contract coverage for `HS256`, the configured shared secret, required `sub`, `iat`, and `exp` claims, and UUID-valued `sub`.
- Gateway identity-boundary coverage for `X-User-Id`, removal of the raw `Authorization` header, non-propagation of extra claims, and separation from ownership authorization.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Current status: `QA IN REVIEW`.

The human approved the Task 3.3 tests and the implementation diff. The implementation agent completed the production change and is handing the task to the QA review state. The final QA agent has not been started, at the user's explicit request.

| Gate | Decision / evidence | Date / approver |
| --- | --- | --- |
| Specification provided | Task 3.3 and the JWT contract in `docs/api-contracts.md` and `docs/tasks/phase-04/02-login-and-jwt.md` were reviewed together with the required project instructions. | 2026-08-08 / test agent |
| Tests in Red | `GatewayJwtAuthenticationIntegrationTest`: 18 tests executed, 5 passed and 13 failed. The Reds were the missing/invalid JWT responses, HS256/claim validation, `X-User-Id` propagation, and removal of the downstream `Authorization` header, all reflecting behavior absent from the current Gateway production code. The valid JWT fixture contains `sub`, `iat`, `exp`, `email`, and `role`; it contains no password, password hash, credential, or other sensitive authentication claim. Full API Gateway suite: 43 tests executed, 30 passed and the same 13 failures remained exclusively in Task 3.3. | 2026-08-08 / test agent |
| Tests approved | Human approved the revised tests after removal of the sensitive `passwordHash` claim from `TOKEN_WITH_EXTRA_CLAIMS`. | 2026-08-08 / user |
| Implementation in Green | JWT validation and identity-boundary implementation completed. The historical routing and CORS/error tests pass. The full Gateway suite passes after the explicitly authorized assertion correction for complete `Authorization` removal and the public-route `X-User-Id` boundary test. | 2026-08-09 / implementation agent |
| Human diff review | Approved by the human. | 2026-08-09 / user |
| QA verdict | Pending. The final QA agent was intentionally not started at the user's request. | — |

Focused command:

```text
./gradlew :services:api-gateway:test --tests com.suaposta.gateway.GatewayJwtAuthenticationIntegrationTest --no-daemon
```

Result: 18 tests executed, 5 passed, and 13 remained Red for the not-yet-implemented Gateway JWT behavior.

Full module command:

```text
./gradlew :services:api-gateway:test --no-daemon
```

Result: 43 tests executed, 30 passed, and 13 failed exclusively in Task 3.3. The existing Task 3.1 and 3.2 tests remained passing.

Compilation and hygiene checks:

```text
./gradlew :services:api-gateway:compileTestJava --no-daemon
git diff --check
```

Both checks passed.

### Approved-test changes

Before human approval, the valid JWT fixture was aligned with the updated JWT contract: `passwordHash` and its dependent non-propagation assertions were removed. The `email` and `role` extra-claim coverage was preserved. The approved test remained unchanged after that approval until the explicitly authorized final-audit correction below.

After the final audit, the human explicitly authorized changing the downstream `Authorization` assertion from `isEmpty()` to `isNull()` to match complete header removal. A focused test was also added to prove that a client-supplied `X-User-Id` is removed from public authentication requests before forwarding to Auth Service.

### QA report

Pending independent QA audit. The final QA agent was not started at the user's request.
