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

Use the status table from `docs/tasks/TEMPLATE.md`.
