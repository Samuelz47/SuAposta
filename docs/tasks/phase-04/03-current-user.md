# 4.3 — Identify the authenticated user

## Context

`GET /auth/me` exposes safe identity data for the currently authenticated user.

The API Gateway validates the Bearer JWT according to Task 3.3, extracts the authenticated user identifier from the `sub` claim, removes the original `Authorization` header, and propagates the authenticated identity internally through `X-User-Id`.

The Auth Service must use this authenticated identity context and must not accept a user identifier from request input.

## Objective

Return the documented identity response for the currently authenticated user through `GET /auth/me`.

## Acceptance criteria

- [ ] `GET /auth/me` is a protected endpoint.
- [ ] A valid JWT accepted by the Gateway returns `200 OK` with the corresponding authenticated user.
- [ ] The response follows the documented API contract.
- [ ] The response contains only safe identity data:
    - `id`;
    - `name`;
    - `email`.
- [ ] Password, password hash, credentials, JWT data, or internal authentication details never appear in the response.
- [ ] The Auth Service obtains the authenticated user identifier exclusively from the trusted internal `X-User-Id` context produced by the Gateway.
- [ ] The endpoint does not accept user identity through path parameters, query parameters, or request body.
- [ ] A user cannot obtain another user's identity by supplying another user identifier in request input.
- [ ] Missing or invalid JWT at the external Gateway boundary returns `401 Unauthorized`.
- [ ] A valid authenticated identity whose corresponding user no longer exists returns `401 Unauthorized`.
- [ ] Authentication failures do not expose whether a user record exists or reveal internal persistence details.

## Authenticated identity contract

For requests forwarded by the Gateway:

```http
X-User-Id: <authenticated-user-uuid>
```

The Auth Service must:

* treat `X-User-Id` as the authenticated identity context supplied by the Gateway;
* require it for `GET /auth/me`;
* require its value to be a valid UUID;
* use it only to locate the current authenticated user;
* never allow request input to override it.

The Auth Service must not require the original Bearer JWT because the Gateway removes the `Authorization` header before forwarding protected requests.

## Error behavior

### Missing or invalid external JWT

Requests reaching the system through the API Gateway without a valid Bearer JWT return:

```text
401 Unauthorized
```

The request must not be forwarded to the Auth Service as an authenticated request.

### Missing or malformed authenticated identity context

If `GET /auth/me` reaches the Auth Service without a valid internal `X-User-Id`, the request returns:

```text
401 Unauthorized
```

### Nonexistent authenticated user

If `X-User-Id` is valid but no corresponding user exists, return:

```text
401 Unauthorized
```

The response must not reveal whether the user was deleted, never existed, or otherwise became unavailable.

All authentication errors must follow the documented safe error contract and must not expose:

* password;
* password hash;
* credentials;
* JWT;
* signing secret;
* stack trace;
* database details;
* internal implementation details.

## Boundary and negative cases

* [ ] Valid authenticated identity returns the corresponding user.
* [ ] Missing JWT at the Gateway returns `401`.
* [ ] Malformed JWT at the Gateway returns `401`.
* [ ] Expired JWT at the Gateway returns `401`.
* [ ] Invalid JWT signature at the Gateway returns `401`.
* [ ] Missing `X-User-Id` at the Auth Service returns `401`.
* [ ] Malformed/non-UUID `X-User-Id` returns `401`.
* [ ] Valid `X-User-Id` for a nonexistent user returns `401`.
* [ ] Query parameters cannot select another user.
* [ ] Request body cannot select another user.
* [ ] Path input cannot override the authenticated identity.
* [ ] Password and password hash never appear in success or error responses.

## Out of scope

* Profile editing.
* Roles administration.
* Resource ownership authorization.
* JWT issuance.
* JWT refresh.
* Password changes.
* Account recovery.
* User deletion.

## Dependencies

* Task 4.1.
* Task 4.2.
* Gateway JWT contract from Task 3.3.

## Expected tests

* Application tests for:

    * authenticated user lookup;
    * nonexistent authenticated subject;
    * safe identity response.

* Auth Service API integration tests for:

    * valid `X-User-Id`;
    * missing `X-User-Id`;
    * malformed `X-User-Id`;
    * nonexistent user;
    * inability to override identity through request input;
    * absence of sensitive data.

* Gateway integration tests, where necessary, to preserve:

    * missing JWT → `401`;
    * malformed JWT → `401`;
    * expired JWT → `401`;
    * invalid signature → `401`;
    * valid JWT → authenticated identity propagated through `X-User-Id`.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.

Current status: `DONE`.

| Current status | Pending gate |
| --- | --- |
| DONE | — |

| Gate | Decision / evidence | Date / approver |
| --- | --- | --- |
| Specification provided | Task 4.3 was reviewed with `docs/domain.md`, `docs/api-contracts.md`, `docs/testing-strategy.md`, `docs/definition-of-done.md`, Task 4.1, Task 4.2, and the Task 3.3 Gateway JWT contract. No unresolved contractual gap or conflict was found. | 2026-08-13 / test agent |
| Tests in Red | Created `AuthCurrentUserApiIntegrationTest` with 9 cases. Compilation and isolated registration setup succeeded. The focused run executed 9 tests: 8 failed only because the current Auth Service returned `404 Not Found` for the not-yet-implemented `GET /auth/me`; the path-input case passed because `/auth/me/{id}` is not exposed. The full Auth Service run executed 40 tests: the 31 pre-existing Task 4.1/4.2 tests passed and only the same 8 new cases remained Red. No build, PostgreSQL, user setup, JWT issuance, or accidental configuration failure occurred. | 2026-08-13 / test agent |
| Tests approved | Human approved the Task 4.3 tests in the implementation request. Approved tests remain unchanged. | 2026-08-12 / human |
| Implementation in Green | Implemented `GET /auth/me`; focused Task 4.3 tests, the full Auth Service check, and the Gateway JWT preservation suite passed. | 2026-08-12 / implementation agent |
| Human diff review | Human approved the implementation diff and authorized handoff to final QA. Approved tests remain unchanged. | 2026-08-12 / human |
| QA verdict | `REJECTED` exclusively because the mandatory application-layer unit tests for `IdentifyCurrentUserService` were missing. No production defect was reported. | 2026-08-12 / QA agent |
| Correction | Added `IdentifyCurrentUserServiceTest` with existing-user, nonexistent-user, missing-identity, and malformed-identity cases. Production code and previously approved tests were not changed. All requested test and check commands passed. | 2026-08-12 / implementation agent |
| QA verdict | `APPROVED` — the application-layer test gap was corrected; Auth Service, API Gateway, root checks, security boundaries, scope, and regression coverage passed. | 2026-08-12 / QA agent |
| QA outcome approved | Human approved the final QA outcome and authorized task completion. | 2026-08-12 / human |

### Approved-test changes

None. Existing Task 3.3, 4.1, and 4.2 tests were not altered, removed, disabled, or weakened.

### Red evidence

Focused command:

```text
./gradlew :services:auth-service:test --tests com.suaposta.auth.AuthCurrentUserApiIntegrationTest --no-daemon
```

Result: 9 tests executed; 8 failed with `expected: 200/401 but was: 404` at `/auth/me`, and 1 path-input case passed with the expected non-success response. PostgreSQL was reachable and both isolated users were created through the approved `POST /auth/register` flow before the cases ran.

Gateway preservation command:

```text
./gradlew :services:api-gateway:test --tests com.suaposta.gateway.GatewayJwtAuthenticationIntegrationTest --no-daemon
```

Result: 19 existing Gateway JWT tests passed, including missing, malformed, expired, invalid-signature, valid-sub propagation, `Authorization` removal, and ownership-boundary coverage. No Gateway test or implementation was changed.
