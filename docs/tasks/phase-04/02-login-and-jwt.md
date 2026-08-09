# 4.2 — Authenticate a user and issue a JWT

## Context

Only registered users with valid credentials can receive a JWT.

The JWT contract defined in this task is also consumed by the API Gateway in Task 3.3.

## Objective

Implement `POST /auth/login` according to the documented contract and issue a signed JWT access token compatible with the Gateway authentication boundary.

## Acceptance criteria

- [ ] Valid credentials return a signed JWT access token and documented user response.
- [ ] JWT access tokens are signed using `HS256`.
- [ ] The signing secret is provided through configuration/environment variables and is never hardcoded in production code or committed to the repository.
- [ ] JWT access tokens contain at least the following claims:
    - `sub`: authenticated user identifier.
    - `iat`: token issuance timestamp.
    - `exp`: token expiration timestamp.
- [ ] `sub` contains the stable identifier required to represent the authenticated user across services.
- [ ] Passwords, password hashes, credentials, or other sensitive authentication data never appear in the response or JWT claims.
- [ ] Invalid credentials return `401` without revealing whether the login or password was incorrect.
- [ ] Tokens issued by the Auth Service can be validated by the API Gateway using the same signing contract.

## Gateway identity contract

After successfully validating the JWT:

- [ ] The Gateway uses the `sub` claim as the authenticated user identity.
- [ ] The Gateway propagates the authenticated identity to downstream services using the internal header `X-User-Id`.
- [ ] The original `Authorization` header containing the Bearer JWT is removed before forwarding the request downstream.
- [ ] JWT claims other than the explicitly required authenticated identity are not propagated as internal headers.
- [ ] Authentication establishes identity only and does not grant ownership authorization over resources.

## Boundary and negative cases

- [ ] Missing credentials are rejected consistently.
- [ ] Malformed credentials are rejected consistently.
- [ ] Incorrect credentials are rejected consistently.
- [ ] An expired JWT is considered invalid by consumers of the token.
- [ ] A JWT with an invalid signature is considered invalid by consumers of the token.
- [ ] Sensitive data must not be exposed through JWT claims, error responses, logs, or propagated identity headers.

## Out of scope

- Refresh tokens.
- Password reset.
- OAuth.
- Social login.
- Role-based authorization beyond the initial contract.
- Resource ownership authorization.
- Service-level ownership enforcement.

## Dependencies

- Task 4.1.
- Task 3.3 consumes the JWT contract defined here.

## Expected tests

- Application unit tests for authentication.
- JWT contract tests for:
    - `HS256` signature.
    - required `sub`, `iat`, and `exp` claims.
    - expiration.
    - invalid signature.
    - absence of sensitive claims.
- API integration tests for `POST /auth/login`.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.