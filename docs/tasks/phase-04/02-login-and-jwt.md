# 4.2 — Authenticate a user and issue a JWT

## Context

Only registered users with valid credentials can receive a JWT.

The JWT contract defined in this task is also consumed by the API Gateway in Task 3.3.

## Objective

Implement `POST /auth/login` according to the documented contract and issue a signed JWT access token compatible with the Gateway authentication boundary.

## Acceptance criteria

* [ ] Valid credentials return `200 OK`.
* [ ] Valid credentials return a signed JWT access token and the documented user response.
* [ ] `tokenType` is `Bearer`.
* [ ] `expiresIn` is `3600`.
* [ ] JWT access tokens are signed using `HS256`.
* [ ] The Auth Service and API Gateway use the same signing secret through the `JWT_SECRET` configuration/environment variable.
* [ ] The signing secret is never hardcoded in production code or committed to the repository.
* [ ] JWT access tokens contain at least the following claims:

  * `sub`: authenticated user identifier.
  * `iat`: token issuance timestamp.
  * `exp`: token expiration timestamp.
* [ ] `sub` contains the stable UUID of the authenticated user.
* [ ] `iat` and `exp` are numeric JWT timestamp claims.
* [ ] `exp` represents an expiration 3600 seconds after issuance.
* [ ] Passwords, password hashes, credentials, signing secrets, or other sensitive authentication data never appear in the response or JWT claims.
* [ ] The submitted password is validated against the persisted BCrypt hash.
* [ ] Invalid credentials return `401 Unauthorized` without revealing whether the email or password was incorrect.
* [ ] Tokens issued by the Auth Service can be validated by the API Gateway using the same signing contract.

## Login validation contract

### Email

* Required.
* Must not be blank after trimming.
* Must use a syntactically valid email format.
* Email is normalized consistently with registration before credential lookup:

  * surrounding whitespace is trimmed;
  * comparison uses lowercase normalization.
* Missing, blank, or malformed email returns `400 Bad Request`.

### Password

* Required.
* Must not be blank.
* Login does not reapply registration password complexity or minimum-length rules.
* Missing or blank password returns `400 Bad Request`.
* Any non-blank password is treated as a credential attempt, regardless of length.
* A non-blank password that does not match the persisted BCrypt hash returns `401 Unauthorized`.

## Validation errors

Structurally invalid login requests return:

```text
400 Bad Request
```

The response must use the standard validation error contract documented in `docs/api-contracts.md`.

Expected validation fields:

```text
email
password
```

The API must identify invalid fields through `fieldErrors`.

Tests should validate the documented error structure and affected field, but should not depend on framework-specific wording unless the exact message is explicitly documented.

Examples of validation failures:

* email missing;
* email blank;
* email malformed;
* password missing;
* password blank.

Validation failures must not issue an access token.

## Invalid credentials contract

A syntactically valid login request that cannot be authenticated returns:

```text
401 Unauthorized
```

This includes:

* email not registered;
* password that does not match the registered user's BCrypt hash.

Both cases must return the same external error behavior.

The response must not reveal:

* whether the email exists;
* whether the password was incorrect;
* password hashes;
* raw passwords;
* signing secrets;
* stack traces;
* database details;
* internal implementation details.

Invalid credentials must never result in an access token being issued.

## JWT access token contract

Access tokens issued by the Auth Service must follow this contract:

* Algorithm: `HS256`.
* Signing secret source: `JWT_SECRET`.
* Token lifetime: `3600` seconds.
* Required claims:

  * `sub`;
  * `iat`;
  * `exp`.
* `sub` must contain the authenticated user's UUID.
* `iat` represents the token issuance time.
* `exp` represents the token expiration time.
* `exp` must correspond to 3600 seconds after `iat`.

The JWT must not contain:

* password;
* password hash;
* credentials;
* signing secret;
* authentication implementation details.

Claims not explicitly required by the initial authentication contract should not be added without a documented contract change.

## Login success contract

A successful login returns:

```text
200 OK
```

The response must follow the contract documented in `docs/api-contracts.md`, including:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": "<user-uuid>",
    "name": "<user-name>",
    "email": "<normalized-email>"
  }
}
```

The response must not contain:

* password;
* password hash;
* signing secret;
* credentials or other sensitive authentication data.

## Gateway identity contract

After successfully validating the JWT:

* [ ] The Gateway uses the `sub` claim as the authenticated user identity.
* [ ] The Gateway propagates the authenticated identity to downstream services using the internal header `X-User-Id`.
* [ ] The original `Authorization` header containing the Bearer JWT is removed before forwarding the request downstream.
* [ ] JWT claims other than the explicitly required authenticated identity are not propagated as internal headers.
* [ ] Authentication establishes identity only and does not grant ownership authorization over resources.

## Boundary and negative cases

* [ ] Missing email returns the documented `400` validation response.
* [ ] Blank email returns the documented `400` validation response.
* [ ] Malformed email returns the documented `400` validation response.
* [ ] Missing password returns the documented `400` validation response.
* [ ] Blank password returns the documented `400` validation response.
* [ ] A non-blank password shorter than the registration minimum is treated as a credential attempt, not as malformed login input.
* [ ] Email not registered returns `401 Unauthorized`.
* [ ] Incorrect password returns `401 Unauthorized`.
* [ ] Email not registered and incorrect password are externally indistinguishable.
* [ ] Invalid requests and invalid credentials do not issue JWTs.
* [ ] An expired JWT is considered invalid by consumers of the token.
* [ ] A JWT with an invalid signature is considered invalid by consumers of the token.
* [ ] Sensitive data must not be exposed through JWT claims, success responses, error responses, logs, or propagated identity headers.

## Out of scope

* Refresh tokens.
* Password reset.
* OAuth.
* Social login.
* Role-based authorization beyond the initial contract.
* Resource ownership authorization.
* Service-level ownership enforcement.
* Account lockout.
* Rate limiting.
* MFA.
* Additional password complexity rules during login.
* JWT claims beyond the documented initial contract.
* Clock skew rules.
* Maximum token lifetime beyond the documented 3600-second access token lifetime.

## Dependencies

* Task 4.1.
* Task 3.3 consumes the JWT contract defined here.

## Expected tests

* Application unit tests for:

  * successful authentication;
  * BCrypt password verification;
  * email normalization;
  * invalid credentials;
  * indistinguishable invalid-email/password behavior.

* JWT contract tests for:

  * `HS256` signature;
  * shared `JWT_SECRET` contract;
  * required `sub`, `iat`, and `exp` claims;
  * UUID value in `sub`;
  * 3600-second token lifetime;
  * expiration;
  * invalid signature;
  * absence of sensitive claims;
  * compatibility with the API Gateway contract.

* API integration tests for:

  * successful `POST /auth/login`;
  * missing email;
  * blank email;
  * malformed email;
  * missing password;
  * blank password;
  * unknown email;
  * incorrect password;
  * absence of sensitive data from success and error responses.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Current status: `DONE`.

| Current status | Pending gate |
| --- | --- |
| DONE | — |

| Gate | Decision / evidence | Date / approver |
| --- | --- | --- |
| Specification provided | Updated Task 4.2 and `docs/api-contracts.md` were reviewed against the current Auth Service, the approved Task 4.1 tests, and the approved Gateway JWT contract from Task 3.3. | 2026-08-12 / test agent |
| Tests in Red | Created `AuthLoginAndJwtApiIntegrationTest` with 14 executed cases. Compilation passed; PostgreSQL setup and isolated user registration passed. All 14 tests failed only because the current Auth Service returned `404 Not Found` for the not-yet-implemented `POST /auth/login`; no build, database, setup, or configuration failure occurred. | 2026-08-12 / test agent |
| Tests approved | Human approved the Task 4.2 tests. | 2026-08-12 / human |
| Implementation in Green | Focused Task 4.2 tests passed; the complete Auth Service test/check suites, API Gateway tests, and root `check` also passed. Approved tests and the Gateway implementation were not changed. | 2026-08-12 / implementation agent |
| Human diff review | Approved after complete human review of the implementation diff; intent-to-add was used to expose new files without definitive staging. | 2026-08-12 / human |
| QA verdict | `APPROVED` — independent QA validated requirements, tests, implementation, security, Gateway compatibility, regression boundaries, and scope. | 2026-08-12 / QA agent |
| QA outcome approved | Human approved the final QA outcome. | 2026-08-12 / human |

### Approved-test changes

None. Tests approved for Tasks 4.1 and 3.3 were not altered, removed, disabled, or weakened.

### Red evidence

Focused command:

```text
./gradlew :services:auth-service:test --tests 'com.suaposta.auth.AuthLoginAndJwtApiIntegrationTest' --no-daemon
```

Result: 14 tests executed, 14 failed with the expected missing-login `404` behavior.

Full Auth Service command:

```text
./gradlew :services:auth-service:test --no-daemon
```

Result: 31 tests executed; the 17 pre-existing tests passed and the 14 Task 4.2 tests remained Red.

Gateway regression command:

```text
./gradlew :services:api-gateway:test --tests 'com.suaposta.gateway.GatewayJwtAuthenticationIntegrationTest' --no-daemon
```

Result: 19 approved Gateway JWT tests passed.

### Green evidence

Focused command:

```text
./gradlew :services:auth-service:test --tests com.suaposta.auth.AuthLoginAndJwtApiIntegrationTest --no-daemon
```

Result: build successful; all approved Task 4.2 login/JWT cases passed.

Required verification commands:

```text
./gradlew :services:auth-service:test
./gradlew :services:auth-service:check
./gradlew :services:api-gateway:test
./gradlew check
git diff --check
git status
git diff
```

Result: all Gradle and diff-check commands completed successfully. The Gateway source was not modified. New implementation files were exposed with intent-to-add for human review; no definitive staging, commit, push, or merge was performed.

### QA report

```text
VERDICT: APPROVED

Blockers:
- None.

Important issues:
- None within the approved Task 4.2 scope.

Non-blocking improvements:
- Consider a consolidated JWT library, injected Clock, explicit JWT_SECRET startup validation, and future secret-strength validation.

Evidence:
- Auth Service tests and check passed.
- API Gateway tests passed.
- Root check passed.
- git diff --check passed.
- Human implementation diff review and final QA outcome approval completed.
```
