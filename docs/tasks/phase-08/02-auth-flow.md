# 8.2 — Implement registration and login flow

## Context

Auth API contracts and Gateway JWT behavior are already defined.

The frontend communicates only with the API Gateway through the HTTP boundary
created in Task 8.1.

Task 8.1 already provides:

- Angular 18 application baseline;
- structural routes;
- centralized Gateway base URL;
- `core/`, `shared/`, and `features/` structure;
- application shell.

Task 8.2 adds real authentication and session behavior to that baseline.

## Objective

Allow a user to register, authenticate, maintain the authenticated frontend
session, and access protected frontend routes through the Gateway.

## Authentication source of truth

Authentication is performed exclusively through the API Gateway.

The frontend must not:

- call `auth-service` directly;
- validate credentials locally;
- generate JWTs;
- modify JWT claims;
- infer server-side authorization rules.

The JWT returned by the authenticated Gateway flow is authoritative for the
frontend session.

## Auth Gateway HTTP contract

Registration uses:

`POST /auth/register`

with the client request fields:

- `name`
- `email`
- `password`

A successful registration returns `201 Created` with the registered user. It
does not return a JWT, does not authenticate the user automatically, and must
redirect the frontend to `/login`.

Login uses:

`POST /auth/login`

with the client request fields:

- `email`
- `password`

A successful login response contains:

- `accessToken`
- `tokenType`
- `expiresIn`
- `user`

The access token contains the `exp` claim. The frontend uses that claim only to
recognize a locally expired session; the Gateway remains authoritative for JWT
validation and authorization.

This contract has no refresh token, refresh endpoint, backend logout, or
server-side session.

## Public routes

The following routes are public:

- `/login`
- `/register`

An unauthenticated user may access them without a route guard.

## Protected routes

The following routes are protected:

- `/dashboard`
- `/bets`

An unauthenticated or locally expired session attempting to access a protected
route must be redirected to:

`/login`

The guard must not perform a direct request to an internal service.

## Registration behavior

The registration form must use the documented Auth/Gateway registration
contract.

The form must:

- expose only fields required by the current registration contract;
- validate required fields before submission;
- preserve server-side validation as authoritative;
- prevent duplicate submission while the request is in progress;
- expose a loading state;
- expose a safe error state.

Successful registration must:

1. not create an authenticated session;
2. redirect the user to `/login`.

Registration must not store a JWT because the registration response does not
return one.

## Login behavior

The login form must use the documented Gateway login contract.

The form must:

- validate required fields before submission;
- prevent duplicate submission while the request is in progress;
- expose a loading state;
- expose a safe authentication error.

Successful login must:

1. persist the returned JWT in `localStorage`;
2. update the frontend session state;
3. redirect the user to `/dashboard`.

The frontend must not store the user's password after submission.

## Session storage

The authenticated JWT must be stored in:

`localStorage`

Use one centrally defined storage key.

Feature components must not independently read or write raw JWT storage.

Session storage access belongs in the authentication/session boundary under
`core/` or the authentication feature infrastructure.

Do not introduce a global state-management library for this task.

A lightweight authentication/session service or Angular signal-based state is
allowed.

## JWT handling

The frontend may inspect the JWT payload only for frontend session concerns
needed by this task, such as expiration.

The frontend must not treat decoded JWT content as a substitute for backend
authorization.

A locally expired JWT must be considered an invalid session.

Malformed/unreadable stored JWT data must be treated as an invalid session.

Do not implement cryptographic JWT signature validation in the browser.

The Gateway remains authoritative.

## Authorization header

Authenticated Gateway requests must use:

`Authorization: Bearer <token>`

A centrally configured Angular HTTP interceptor must attach the header to
eligible Gateway requests when a valid local session exists.

Feature API services must not manually construct Authorization headers.

The interceptor must not send an Authorization header when no authenticated
session exists.

The frontend sends only the `Authorization` Bearer token for authenticated
identity. It must not send `X-User-Id`. The Gateway removes any client-supplied
`X-User-Id` and injects the trusted user identity internally after validating
the JWT.

## 401 behavior

When an authenticated Gateway request returns:

`401 Unauthorized`

the frontend must:

1. clear the stored session;
2. update local authentication state;
3. redirect the user to `/login`.

Do not expose backend stack traces or internal error details.

Do not create infinite redirect/request loops for a `401` received by the login
or registration request itself.

Registration validation failures return `400 Bad Request` and may include
`fieldErrors`. Invalid login credentials return `401 Unauthorized`. A missing,
malformed, expired, or invalid JWT on a protected Gateway route returns `401`
with the safe generic Gateway error; it is not a login-form validation response.

## Logout

The authenticated UI must provide a logout action.

Logout must:

1. clear the locally stored JWT;
2. clear local authentication state;
3. redirect to `/login`.

There is no backend logout/revocation endpoint or server-side session in the
current contract.

## Already-authenticated public-route behavior

If a user with a locally valid authenticated session navigates to:

- `/login`
- `/register`

redirect to:

`/dashboard`

This behavior should be handled consistently by routing/session infrastructure,
not duplicated across page components.

## Form validation

Client-side validation exists for user experience only.

Backend validation remains authoritative.

Validation behavior must distinguish at minimum:

- invalid/missing local form fields;
- authentication/registration API failure;
- request in progress;
- request success.

Do not expose raw server exception content.

## Safe API errors

UI error messages must be safe and user-facing.

Do not display:

- stack traces;
- SQL details;
- internal service hostnames;
- Java exception class names;
- raw infrastructure errors.

If the Gateway response contains a safe documented message, it may be used.

Otherwise use a generic authentication/registration failure message.

## Loading state

Login and registration submissions must expose an explicit loading state.

While loading:

- repeated submission must be prevented;
- submit control must reflect the in-progress state.

The request must not be triggered more than once by a single user action.

## API boundary

All authentication HTTP requests must use the centralized Gateway base URL
created in Task 8.1.

No authentication production code may contain direct URLs for:

- auth-service;
- betting-service;
- analytics-service;
- internal service ports.

## Acceptance criteria

- [ ] Registration form uses the documented Gateway registration contract.
- [ ] Login form uses the documented Gateway login contract.
- [ ] Required fields are validated before submission.
- [ ] Registration and login expose loading states.
- [ ] Safe API error states are shown.
- [ ] Successful registration redirects to `/login`.
- [ ] Successful login stores the JWT in `localStorage`.
- [ ] Successful login redirects to `/dashboard`.
- [ ] JWT storage is centralized and does not leak into feature components.
- [ ] Authenticated Gateway requests receive `Authorization: Bearer <token>`.
- [ ] Frontend authenticated requests do not send `X-User-Id`.
- [ ] Feature API services do not manually attach JWT headers.
- [ ] Missing session prevents access to protected routes.
- [ ] Expired stored JWT prevents access to protected routes.
- [ ] Malformed stored session data is treated as unauthenticated.
- [ ] Unauthorized protected navigation redirects to `/login`.
- [ ] A locally valid authenticated session visiting `/login` or `/register`
  redirects to `/dashboard`.
- [ ] Gateway `401` clears the session and redirects to `/login`.
- [ ] Login/registration failures do not create redirect loops.
- [ ] Logout clears the session and redirects to `/login`.
- [ ] No direct request to `auth-service` exists in frontend production code.
- [ ] No global state-management library is introduced.

## Boundary and negative cases

- [ ] Missing required login fields do not submit.
- [ ] Missing required registration fields do not submit.
- [ ] Invalid credentials are handled safely.
- [ ] Failed registration is handled safely.
- [ ] Duplicate submit is prevented while loading.
- [ ] Missing token is treated as unauthenticated.
- [ ] Malformed stored token is treated as unauthenticated.
- [ ] Expired token is treated as unauthenticated.
- [ ] Gateway `401` invalidates the local session.
- [ ] Registration `400` validation, invalid-login `401`, and generic Gateway
  JWT `401` are handled as their distinct documented cases.
- [ ] Login `401` does not cause an interceptor redirect loop.
- [ ] Registration failure does not create an authenticated session.
- [ ] Logout removes the persisted session.
- [ ] Internal service URLs are not used.

## Out of scope

- Password reset.
- OAuth/social login.
- MFA.
- Refresh tokens and refresh endpoints.
- Backend logout.
- Server-side sessions.
- Server-side JWT revocation.
- Profile editing.
- Role/permission administration UI.
- Persistent global state-management library.
- Betting behavior.
- Dashboard behavior.

## Dependencies

- Tasks 4.1–4.3.
- Task 8.1.
- Gateway Auth route contract.

## Expected tests

Focused Angular tests must cover at least:

- login form validation;
- registration form validation;
- login loading state;
- registration loading state;
- successful login;
- failed login;
- successful registration;
- failed registration;
- JWT persistence;
- session restoration from storage;
- expired/malformed token handling;
- authentication interceptor;
- absence of Authorization header without a session;
- absence of client-supplied `X-User-Id`;
- route guard for protected routes;
- authenticated redirect away from `/login` and `/register`;
- `401` session invalidation;
- prevention of login/registration `401` redirect loops;
- registration validation, invalid-login, and generic Gateway JWT error cases;
- logout;
- Gateway-only Auth API targeting.

Tests must not depend on Task 8.3 production behavior.

## Required verification

The task must successfully execute:

- frontend unit tests;
- production build;
- documented local serve flow.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

| Field | Value |
| --- | --- |
| Status | `DONE` |
| Red tests | Initial implementation baseline created 24 Task 8.2 behavioral cases in `task82-auth-forms.spec.ts`, `task82-session-routing.spec.ts`, and `task82-auth-http.spec.ts`; the pre-implementation suite had 50 cases with 28 GREEN and 22 deterministic behavioral RED cases. |
| Human test approval | `APPROVED` by human on 2026-09-16. |
| Protected Task81 tests modified | `YES`, with explicit human authorization on 2026-09-17 to update `task81-routing.spec.ts` for the now-protected dashboard and bets routes. |
| Task81 regression | GREEN: all 26 Task 8.1 baseline/supporting cases passed in the final complete run; the isolated routing, shell, and configuration selection passed 16/16 after the authorized compatibility update. |
| Frontend baseline | `npm ci` passed; pre-Task82 frontend test baseline passed with 26/26; `npm run build` passed. |
| Gateway regression | `./gradlew :services:api-gateway:test --rerun-tasks` passed. |
| Auth backend baseline | `INFRASTRUCTURE BLOCKED`: 12 tests completed, 8 passed, and 4 integration initializations failed because PostgreSQL at `127.0.0.1:5432` was unavailable. |
| Human implementation/diff approval | `APPROVED` by human on 2026-09-17, including the protected-test compatibility decision; implementation is ready for independent QA. |
| Intent-to-add | Only `git add -N` is used for new Task 8.2 production files and supporting specs before QA; no regular staging, commit, push, or merge. |
| Production changes | Added centralized auth API/session models, strict local JWT format validation, Gateway interceptor, route guards, login/register forms, controlled safe errors, and logout actions. No backend, migration, package, or Task 8.3 changes. |

### Task 8.2 test evidence

| Area | Coverage / result |
| --- | --- |
| Forms | Login and registration semantic fields, absence of password confirmation, required-field no-submit behavior, loading and duplicate-submit protection — GREEN. |
| Requests | Exact Gateway `POST /auth/login` and `POST /auth/register` targets and exact request fields; public auth requests assert no `Authorization` or client `X-User-Id` — GREEN. |
| Success/failure | Login persistence/redirect/password secrecy, registration `201` redirect without session, login `401` no-loop, registration `400`/`409`, and safe error feedback — GREEN. |
| Session/routing | Missing-session guards, malformed/expired token invalidation, valid-session protected navigation, authenticated public-route redirects, storage restoration, and logout — GREEN. |
| HTTP boundary | No-session requests remain free of identity headers; authenticated bearer propagation, `X-User-Id` absence, and authenticated `401` invalidation — GREEN. |

### Verification commands

| Verification | Result |
| --- | --- |
| Baseline install | `npm ci` — PASS. |
| Baseline frontend test | `npm test -- --watch=false --browsers=ChromeHeadless` — 26/26 GREEN before Task 8.2 tests. The initial sandbox attempt could not bind Karma port 9876; the same command succeeded with the required local-runner permission. |
| Baseline production build | `npm run build` — PASS with the required local-runner permission; the initial sandbox attempt exited 134 before producing a build result. |
| Task 8.2 run 1 | `npm test -- --watch=false --browsers=ChromeHeadless` — 50 total, 28 GREEN, 22 RED. |
| Task 8.2 run 2 | Same command — 50 total, 28 GREEN, 22 RED. |
| RED determinism | PASS: the same 22 Task 8.2 behavioral failures occurred in both runs; no compile/load failure occurred. |
| Pre-correction frontend test | `npm test -- --watch=false --browsers=ChromeHeadless` — 50/50 GREEN before final QA identified the two blockers. |
| Pre-correction production build | `npm run build` — PASS. |
| Pre-correction local serve sanity | `npm start` — PASS at `http://localhost:4200`; `curl --fail` returned HTTP 200. |
| Correction frontend run 1 | `npm test -- --watch=false --browsers=ChromeHeadless` — 61/61 GREEN. |
| Correction frontend run 2 | Same command — 61/61 GREEN. |
| Correction production build | `npm run build` — PASS. |
| Correction local serve sanity | `npm start` — PASS at `http://localhost:4200`; `curl --fail` returned HTTP 200. |
| Correction Gateway regression | `./gradlew :services:api-gateway:test --rerun-tasks` — BUILD SUCCESSFUL. |
| Correction Auth regression | `INFRASTRUCTURE BLOCKED`: 12 tests completed; 4 integration initializations failed because PostgreSQL at `127.0.0.1:5432` was unavailable, while the remaining tests passed. |
| Correction root regression | `INFRASTRUCTURE BLOCKED`: 17 integration initializations failed — 6 Analytics Testcontainers, 4 Auth PostgreSQL, and 7 Betting PostgreSQL/RabbitMQ — because Docker/PostgreSQL were unavailable; unaffected tests compiled and ran. |
| Diff checks | `git diff --check` — PASS; only intent-to-add is permitted before QA, with no regular staging. |

### Deferred assertions

- Exact private `localStorage` key ownership/source scanning is deferred because the contract intentionally does not freeze a key or service/class name; successful-login tests discover the created entry behaviorally.
- A source-text scan for internal service URLs is deferred; the public Gateway request contract and the protected Task 8.1 Gateway boundary tests cover the available stable surface without scanning tests, documentation, dependencies, or generated output.

### Approved-test changes

On 2026-09-17 the human authorized the compatibility update in
`apps/web/src/app/task81-routing.spec.ts`. The protected Task 8.1 assertions
previously expected unauthenticated `/dashboard` and `/bets` navigation to
remain on those URLs, while the approved Task 8.2 contract requires both to
redirect to `/login`. The root and unknown-route expectations were updated for
the same protected dashboard entry behavior. No Task 8.2 test was changed,
weakened, skipped, or removed.

### Initial QA report before remediation

The initial final QA identified two blockers: malformed JWT header/signature
Base64URL validation and blacklist-based authentication-error sanitization.
The task remained `QA IN REVIEW` pending remediation and a final independent
rerun.

### QA correction after rejected final QA — 2026-09-17

The final QA blockers were corrected without changing the backend or protected
tests:

- Blocker 1: `AuthSessionService` now validates Base64URL syntax and decoding
  for all three JWT segments, parses header and payload as JSON objects, and
  checks finite future `exp`; it performs no cryptographic verification.
- Blocker 2: authentication errors now use controlled frontend messages mapped
  by context and HTTP status. Arbitrary backend error messages are never
  rendered, including infrastructure details such as `SQLSTATE ... db.internal`.
- Supporting tests were added for malformed JWT headers/payloads/signatures,
  invalid or expired `exp`, valid synthetic JWTs, and safe error mappings.
- `LogoutButtonComponent` was confirmed live and remains referenced by the
  dashboard and bets pages; no dead-code cleanup was necessary.
- Route inspection confirms `login`, `register`, `dashboard`, and `bets` remain
  lazy through `loadComponent`.

The task is ready for QA to re-run. The only remaining verification limitation
is infrastructure availability for PostgreSQL and Docker-backed Gradle tests.

### Final QA verdict — 2026-09-17

Independent final QA was re-run after both remediation blockers were corrected.
The frontend suite passed 61/61 twice, the production build passed, the local
server returned HTTP 200, and the Gateway regression passed. The malformed JWT
Base64URL cases and controlled authentication-error mapping cases passed
explicitly. Auth and root Gradle integration coverage remained blocked only by
the unavailable Docker/PostgreSQL environment; no Task 8.2 assertion failure
was observed.

Final verdict: `APPROVED`.

The human approved the final QA outcome on 2026-09-17. The controlled roadmap
transition from `QA IN REVIEW` to `DONE` is therefore complete. QA made no
production or test-code changes.
