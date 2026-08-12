# 4.1 — Register a user

## Context

Auth owns user identity. See `docs/domain.md` and `docs/api-contracts.md`.

## Objective

Register a new user securely through `POST /auth/register`.

## Acceptance criteria

- [ ] A valid new user receives `201 Created`.
- [ ] The success response follows the documented API contract.
- [ ] Password and password hash never appear in the response.
- [ ] Email is required.
- [ ] Email must use a syntactically valid email format.
- [ ] Email is normalized by trimming surrounding whitespace and storing it in lowercase.
- [ ] Email uniqueness is case-insensitive.
- [ ] Registering an already existing email returns `409 Conflict`.
- [ ] Password is required.
- [ ] Password must contain at least 8 characters.
- [ ] Password is stored only as a BCrypt hash.
- [ ] The raw password is never persisted.
- [ ] Invalid registration input returns the documented validation response.
- [ ] Invalid requests do not persist a user.

## Validation contract

### Name

- Required.
- Must not be blank after trimming.

### Email

- Required.
- Must not be blank after trimming.
- Must be syntactically valid.
- Must be normalized to lowercase before persistence.
- Uniqueness comparison is case-insensitive.

### Password

- Required.
- Must not be blank.
- Minimum length: 8 characters.
- No additional complexity rules are required in the initial version.

## Validation errors

Invalid registration input returns `400 Bad Request` using the standard validation error shape documented in `docs/api-contracts.md`.

Expected field names:

```text
name
email
password
```

The API must identify the invalid field through fieldErrors.

Tests should validate the documented error structure and affected field, but should not depend on framework-specific wording unless the exact message is explicitly documented.

Duplicate email behavior

Registering an email that already exists after normalization returns:

409 Conflict

The response must follow the standard error contract documented for POST /auth/register.

The duplicate attempt must not create another user.

Password storage
Passwords must be hashed using BCrypt before persistence.
The persisted value must not equal the raw password.
Password hashes must never be returned by the API.
Tests should verify the stored hash matches the submitted password through BCrypt verification rather than asserting a specific generated hash value.
Boundary and negative cases
 Blank name.
 Blank email.
 Malformed email.
 Email containing surrounding whitespace.
 Email differing only by case from an existing account.
 Blank password.
 Password shorter than 8 characters.
 Duplicate email.
 Password exposure in success or error responses.
Out of scope
Login.
JWT issuance.
Refresh tokens.
Password reset.
OAuth.
Social login.
Roles administration.
Password complexity rules beyond the minimum length.
Email verification.
Dependencies
Phase 2 Auth skeleton.
Service database infrastructure.
Expected tests
Domain/application tests for registration rules.
API integration tests for success, validation, and duplicate email.
Persistence integration tests for:
normalized email;
uniqueness;
BCrypt password storage;
absence of raw password persistence.
Definition of Done

Apply docs/definition-of-done.md.

Status and evidence

| Current status | Pending gate |
| --- | --- |
| DONE | — |

| Gate | Decision / evidence | Date / approver |
| --- | --- | --- |
| Specification provided | Updated registration contract reviewed against the domain, API, architecture, testing, and infrastructure documents. | 2026-08-11 / test agent |
| Tests in Red | Focused Task 4.1 suite executed 13 tests: 13 failed because the current Auth Service returns `404` for `POST /auth/register`; no build, PostgreSQL connectivity, or configuration failure occurred. The full Auth Service suite executed 17 tests: the 4 pre-existing tests passed and the 13 Task 4.1 tests remained Red for the same missing endpoint. | 2026-08-11 / test agent |
| Tests approved | Human approved the Red tests. Approved test files remain unchanged after approval. | 2026-08-11 / human |
| Implementation in Green | Focused Task 4.1 tests passed: 13 tests; full Auth Service suite passed: 17 tests; `:services:auth-service:check` passed including Java 21 verification. PostgreSQL constraint and BCrypt persistence were also verified directly. | 2026-08-11 / implementation agent |
| Human diff review | Human approved the implementation diff. Approved tests were not altered, removed, disabled, or weakened. | 2026-08-11 / human |
| QA verdict | `APPROVED` — independent final QA validated the requirements, approved tests, implementation, persistence, security, Gateway compatibility, scope, and regression boundaries. | 2026-08-11 / QA agent |
| QA outcome approved | Human approved the final QA outcome and authorized task completion. | 2026-08-11 / human |

### Approved-test changes

None.

### Red evidence

Focused command:

```bash
./gradlew :services:auth-service:test --tests 'com.suaposta.auth.AuthRegistration*IntegrationTest' --no-daemon
```

The local PostgreSQL container was healthy and accepting connections before the persistence tests. The current `auth_db` was inspected read-only after implementation. No production code was changed during QA.

### Final QA evidence

```text
VERDICT: APPROVED
Commands: ./gradlew :services:auth-service:test; ./gradlew :services:auth-service:check; ./gradlew check; git diff --check; git status; git diff
PostgreSQL: healthy; V1 applied; ux_users_email_lower exists on LOWER(email); persisted hashes have BCrypt $2a$ prefix and length 60.
Gateway: POST /auth/register remains routed through /auth/** and public at the Gateway boundary.
Scope: no login, JWT issuance, refresh token, roles, or future authentication behavior was added.
Approved tests: not removed, disabled, altered, or weakened.
```
