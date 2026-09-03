# 8.1 — Bootstrap Angular app, layout, and API boundary

## Context

The frontend calls only the API Gateway and follows the feature/core/shared
structure defined in `docs/architecture.md`.

The web application lives under `apps/web/`.

## Objective

Create the Angular 18 application baseline, application shell, structural
routes, local development configuration, and the single HTTP boundary through
which future frontend features access the Gateway.

## Technical baseline

- Angular 18.
- Standalone application and components.
- Angular Router enabled.
- TypeScript strict mode enabled.
- Strict Angular templates enabled.
- SCSS.
- npm with `package-lock.json` versioned.
- No SSR or SSG.
- No global state-management library in this task.
- No NgRx, Redux, Akita, or equivalent dependency.

## Application structure

The baseline must establish:

- `core/` for application-wide infrastructure and HTTP configuration.
- `shared/` for reusable presentation code with no feature business behavior.
- `features/` for feature-owned pages/components/services.

Do not create speculative abstractions or feature implementations that belong
to Tasks 8.2 or 8.3.

## Application shell

Create the minimal application shell required to host routed content.

It must provide a stable layout area for:

- global navigation;
- routed page content.

Visual polish and an advanced design system are out of scope.

## Routes

The baseline must define structural routes for:

- `/login`
- `/register`
- `/dashboard`
- `/bets`

Task 8.1 may use minimal placeholder/page-shell components.

Authentication behavior, guards, login forms, betting behavior, and dashboard
behavior are NOT implemented yet.

`/` redirects to `/dashboard`.

Unknown routes redirect to `/dashboard`.

Feature routes/pages should be lazy loaded where supported by the chosen
standalone Angular structure.

## Gateway HTTP boundary

All application HTTP traffic must target only the API Gateway.

The Gateway base URL must:

- be defined in one application-wide configuration boundary;
- come from the documented frontend environment/local configuration;
- never be duplicated across feature services;
- never be hardcoded inside feature services.

Feature HTTP services must use the configured Gateway base URL plus relative
API paths.

No application service may directly target:

- auth-service;
- betting-service;
- analytics-service;
- any other internal service host or port.

Internal-service URLs must not be present in frontend production code.

## Local Gateway contract

The canonical frontend location is:

`apps/web/`

For local development:

- Angular dev origin: `http://localhost:4200`.
- API Gateway base URL: `http://localhost:8080`.
- API paths have no global prefix; for example, use `/auth/login`, `/bets`, and
  `/analytics/dashboard` relative to the configured Gateway base URL.
- The browser communicates directly with the Gateway through the documented
  Gateway CORS contract.
- An Angular development proxy is not part of this contract. Do not create
  `proxy.conf` or equivalent proxy configuration.
- Every feature API must use the one central Gateway configuration boundary.

## Acceptance criteria

- [ ] Angular 18 application exists under `apps/web/`.
- [ ] Clean install from the repository lock file succeeds.
- [ ] Application builds successfully.
- [ ] Application serves using the documented local configuration.
- [ ] TypeScript and Angular strict modes are enabled.
- [ ] Standalone Angular structure is used.
- [ ] `core/`, `shared/`, and `features/` boundaries exist.
- [ ] Minimal application shell renders routed content.
- [ ] `/login`, `/register`, `/dashboard`, and `/bets` structural routes exist.
- [ ] `/` redirects to `/dashboard`.
- [ ] Unknown routes follow the documented fallback behavior.
- [ ] Gateway base URL is configured centrally.
- [ ] Application HTTP services can target only the Gateway.
- [ ] Local development uses `http://localhost:4200` directly against
  `http://localhost:8080` through CORS, without an Angular development proxy.
- [ ] Feature code does not contain internal-service URLs.
- [ ] No global state-management library is introduced.

## Boundary and negative cases

- [ ] Feature HTTP services do not hardcode hosts or ports.
- [ ] No request to an internal service URL is possible through application
  services.
- [ ] Changing the configured Gateway base URL changes the HTTP target without
  changing feature-service code.
- [ ] Feature code does not add a global API prefix to documented Gateway paths.
- [ ] No `proxy.conf` or equivalent Angular development proxy is introduced.
- [ ] Task 8.1 does not implement authentication/session behavior.
- [ ] Task 8.1 does not implement betting or analytics business UI.

## Out of scope

- Registration behavior.
- Login behavior.
- JWT storage.
- HTTP authentication interceptor.
- Route guards.
- Logout behavior.
- Betting UI.
- Dashboard UI.
- Global application state management.
- Advanced design system.
- SSR/SSG.

## Dependencies

- Gateway route and local runtime contract.

## Expected tests

Focused Angular tests must cover at least:

- structural route configuration;
- root redirect;
- route fallback;
- application shell rendering;
- Gateway base URL configuration;
- HTTP service URL construction using the Gateway boundary;
- absence of direct internal-service targeting in application services.

Tests must not require Tasks 8.2 or 8.3 production behavior.

## Required verification

The task must document and successfully execute the frontend equivalents of:

- clean dependency installation;
- unit tests;
- production build.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

| Field | Value |
| --- | --- |
| Status | `QA IN REVIEW` |
| Red tests | Created `task81-routing.spec.ts`, `task81-shell.spec.ts`, and `task81-configuration.spec.ts`. The full Karma suite compiled and executed 17 tests, with 8 deterministic behavioral REDs and 9 GREEN baseline/configuration tests in both runs. |
| Strict TypeScript coverage | `strictNullChecks` is protected by the compile-time test sentinel; effective `strict=true` is verified by `@angular/compiler-cli` configuration evidence and the production build. |
| Human test approval | Approved by human on 2026-09-03. |
| Implementation | Complete: approved bootstrap-fixture correction applied, two final frontend runs GREEN (26/26 each), production build GREEN. |
| Human implementation approval | APPROVED on 2026-09-03: human approved the proposed fixture correction and requested completion for final QA. |
| Final QA | `PENDING` |
| Evidence | Baseline `npm ci`, Angular baseline test (`1 SUCCESS`), production build, and `npm start` on `http://localhost:4200` passed before the new specs. `./gradlew :services:api-gateway:test --rerun-tasks` passed. Effective Angular compiler configuration resolved with `@angular/compiler-cli`: `strict=true`, `strictTemplates=true`, no errors. |
| Original blind RED run 1 | `npm test -- --watch=false --browsers=ChromeHeadless`: 17 total, 9 GREEN, 8 RED. REDs: four structural routes (`/login`, `/register`, `/dashboard`, `/bets`) failed with `NG04002`; root redirect remained `/`; wildcard navigation failed with `NG04002`; the lazy-boundary test failed at its first real navigation because `/login` was absent; shell had no navigation or `router-outlet`. |
| Original blind RED run 2 | `npm test -- --watch=false --browsers=ChromeHeadless`: 17 total, 9 GREEN, 8 RED, with the same failing behaviors. |
| Final frontend run 1 | `npm test -- --watch=false --browsers=ChromeHeadless`: 26/26 GREEN after the human-approved fixture correction. |
| Final frontend run 2 | Same command and result: 26/26 GREEN, no skipped tests. |
| Gateway regression | `./gradlew :services:api-gateway:test --rerun-tasks` — `BUILD SUCCESSFUL`. |
| Intent-to-add | Used only `git add -N` for the three new Task 8.1 spec files so they are visible in `git diff`; no regular staging, commit, push, or merge. |

### Approved-test changes

Human approval received on 2026-09-03 in this session: "aprovado bb, finalize sua
etapa pra eu mandar pro agente qa final". This approves the previously proposed
correction to the two bootstrap fixtures and the implementation handoff to QA.

- Files: `apps/web/src/app/task81-configuration.spec.ts` (protected standalone
  bootstrap case) and `apps/web/src/app/app.component.spec.ts` (scaffold case).
- Authorized correction: import `appConfig` and provide
  `providers: [...appConfig.providers]` in those two test setups.
- Reason: `RouterLink` requires `ActivatedRoute` from the real standalone router
  configuration; the previous fixtures omitted those providers.
- Affected acceptance criteria: standalone Angular structure and the minimal
  navigation/routed-content shell. Their behavior and all assertions remain
  unchanged. No test is removed, skipped, weakened, or mocked.
- The routing and shell protected test files remain unchanged. Any further
  protected-test change still requires explicit human approval.

The approved correction passed both complete frontend runs. Assertions and test
bodies were compared to the approved originals: only the stated import/provider
additions and formatting of that setup changed.

### Implementation gate — 2026-09-03

| Current status | Pending gate |
| --- | --- |
| QA IN REVIEW | Independent QA audit and human approval of its outcome. |

| Gate | Decision / evidence | Date / approver |
| --- | --- | --- |
| Specification provided | Task 8.1 implementation explicitly requested; Tasks 8.2/8.3 excluded. | 2026-09-03 / human |
| Tests in Red | Reproduced before production changes: `npm test -- --watch=false --browsers=ChromeHeadless` in `apps/web`: 17 total, 9 GREEN, 8 RED. The protected files contain 16 Task81 cases; the scaffold adds 1 case. | 2026-09-03 / implementation agent |
| Tests approved | APPROVED; explicit human instruction in this implementation session confirms the bootstrap and blind RED tests are approved. | 2026-09-03 / human |
| Implementation in Green | Two final runs: 26/26 GREEN each; clean install and production build passed. Earlier 24 GREEN / 2 RED fixture failures were resolved only after the approval recorded above. | 2026-09-03 / implementation agent |
| Human diff review | APPROVED: "aprovado bb, finalize sua etapa pra eu mandar pro agente qa final"; production diff and the proposed fixture correction approved for handoff. | 2026-09-03 / human |
| QA verdict | PENDING; human will hand off to the independent QA agent. | — |

Transitions: `TESTS IN REVIEW` -> `IMPLEMENTATION IN PROGRESS` was recorded before
functional production changes. After the subsequent human approval and final
GREEN verification, `IMPLEMENTATION IN PROGRESS` -> `QA IN REVIEW` was recorded
and synchronized with the roadmap. The initial instruction to await human diff
approval has therefore been satisfied. `DONE`, commits, push, and merge remain
outside this implementation handoff.

Pre-implementation REDs are the four absent structural routes, root and wildcard
redirects, lazy routing, and navigation/outlet shell. The first sandboxed run
could not bind Karma port 9876 (`EPERM`); the authorized rerun outside that
restriction compiled and executed the real tests. Evidence:
`/tmp/task81-pre-implementation.log`.

### Deferred assertions at the test-agent gate

The following records describe the earlier scaffold-only audit. The public
Gateway boundary and supporting evidence added during implementation follow
below; independent QA still owns its final verification.

- Gateway centralized configuration, URL construction, configurable override,
  and `X-User-Id` absence were deferred because the scaffold exposed no
  neutral public HTTP boundary/client. Importing an invented class/token would
  make the RED a test design failure rather than missing Task 8.1 behavior.
- The automated production source-boundary scan was deferred because the scaffold
  exposed no source manifest; the implementation source inspection found no
  internal-service URLs.
- Exact automated inspection of effective `strictTemplates` is deferred in the
  Karma browser suite because the source `tsconfig` files are JSONC with an
  `extends` chain. Final QA must revalidate it through the effective
  configuration and production build. The effective value was independently
  resolved with `@angular/compiler-cli` and is recorded above.

### Implementation evidence — 2026-09-03

Result: implementation complete and human-approved; ready for independent final
QA. The protected-test fixture conflict below is resolved by the recorded human
decision, not by changing production or weakening expectations.

Production now provides four lazy standalone heading-only pages, the documented
redirects, and an application shell under `core/layout/` composed by
`AppComponent`. `core/http/GatewayHttpClient` uses Angular `HttpClient` configured
with `provideHttpClient()` and the replaceable `GATEWAY_BASE_URL` token. Its
default is `http://localhost:8080`. It joins relative paths without a global
prefix, normalizes only the joining slash, and rejects absolute and
protocol-relative targets. It adds no identity or Authorization headers.

The `shared/README.md` records ownership without speculative shared components.
Structural pages do not make HTTP calls. The reusable dependency direction for
future feature services is feature -> core HTTP boundary -> Angular HttpClient
-> configured Gateway. No Auth, Betting, or Analytics business flow was added.

#### Resolved protected test conflict (historical evidence)

- Protected test (original line 43): `task81-configuration.spec.ts`,
  `should support standalone component bootstrap without an NgModule`.
- Original setup: `TestBed.configureTestingModule({ imports: [AppComponent] })` omitted
  `appConfig.providers`; creating the component failed with
  `NullInjectorError: No provider for ActivatedRoute!`.
- The original scaffold case `app.component.spec.ts:11` had the same missing
  router-provider setup and failure.
- Contract: standalone bootstrap and a working Angular navigation shell.
  The real `main.ts` passes `appConfig` to `bootstrapApplication`; `appConfig`
  includes `provideRouter(routes)`. Angular 18's `RouterLink` requires
  `ActivatedRoute`, which `provideRouter` supplies.
- Evidence: the shell test already uses `appConfig.providers` and passes. All
  seven protected routing cases pass. Moving the shell into its own component
  does not remove the requirement for router providers when the root is created.
- Why this implementation could not satisfy the original fixture: it obtains
  `ActivatedRoute` through the standard application router configuration; the
  original fixture imported only the component and never installed that
  configuration. Standalone components still need application providers.
- Human decision and resolution: on 2026-09-03 the human approved importing
  `appConfig` and adding `providers: [...appConfig.providers]` to both bootstrap
  fixtures. That exact setup-only change is now applied, preserving all
  assertions. Both cases pass in the final two runs.

#### Verification results

| Verification | Actual result |
| --- | --- |
| `npm ci` in `apps/web` | PASS, repeated after approval: 950 packages installed; package manifest and lock file unchanged. |
| Pre-implementation frontend | 17 total: 16 protected (8 GREEN, 8 RED), 1 scaffold GREEN. |
| Before fixture approval, run 1 | `npm test -- --watch=false --browsers=ChromeHeadless`: 26 total, 24 GREEN, 2 RED. |
| Before fixture approval, run 2 | Same command and result: 26 total, 24 GREEN, 2 RED, identical failing cases. |
| Final frontend run 1 | `npm test -- --watch=false --browsers=ChromeHeadless`: 26/26 GREEN, 0 failures, 0 skipped. |
| Final frontend run 2 | Same command and result: 26/26 GREEN, 0 failures, 0 skipped. |
| Protected Task81 count/result | 16/16 GREEN; only the approved bootstrap-fixture setup changed. |
| IMPLEMENTER-ADDED SUPPORTING TESTS | 9/9 GREEN in `core/http/gateway-http-client.spec.ts`. |
| Scaffold count/result | 1/1 GREEN after the approved fixture correction. |
| Production build | `npm run build`: PASS, repeated after the two final GREEN runs; four distinct lazy page chunks, initial bundle 252.60 kB. |
| Effective compiler options | `@angular/compiler-cli.readConfiguration` for app and spec: `strict=true`, `strictTemplates=true`, no configuration errors. |
| Serve sanity | `npm start`: PASS at `http://localhost:4200`; local `curl --fail` returned HTTP 200. Server stopped afterward. |
| Gateway regression | `./gradlew :services:api-gateway:test --rerun-tasks`: PASS, 44 tests, 0 failures. |
| Root regression | `./gradlew check --rerun-tasks`: `ROOT CHECK: INFRASTRUCTURE BLOCKED`. All 17 failures are PostgreSQL connection refusal at `127.0.0.1:5432` or unavailable Docker/Testcontainers (Auth 4, Betting 7, Analytics 6). No backend changes made. |
| npm audit | 57 vulnerabilities: 7 low, 17 moderate, 32 high, 1 critical; unchanged from the reported baseline. No audit fix or dependency update. |

Supporting tests use `HttpTestingController` with the real app providers and
Angular's testing HTTP backend. They cover default targeting and absent headers,
configuration override, three joining-slash variants, preserving path content,
query parameter forwarding, and two invalid direct-target forms. They make no
real network requests and do not replace the protected tests.

Node 22.23.2, npm 10.9.8, Angular 18.2.14, CLI 18.2.21, and Java 21 were used.
The sandbox initially prevented Karma binding, Gradle cache access, and npm audit
DNS access; authorized reruns completed. The sandboxed build aborted with exit
134; its authorized rerun passed. No test or build configuration was changed to
bypass these restrictions.

Raw local evidence is in `/tmp/task81-pre-implementation.log`,
`/tmp/task81-frontend-run-1.log`, `/tmp/task81-frontend-run-2.log`,
`/tmp/task81-build.log`, `/tmp/task81-serve.log`,
`/tmp/task81-gateway-regression.log`, `/tmp/task81-root-regression.log`,
`/tmp/task81-npm-ci.log`, and `/tmp/task81-npm-audit.json`.

Final post-approval evidence: `/tmp/task81-approved-npm-ci.log`,
`/tmp/task81-approved-frontend-run-1.log`,
`/tmp/task81-approved-frontend-run-2.log`, and `/tmp/task81-approved-build.log`.
Earlier failure logs are retained as historical evidence. Gateway, root, serve,
and audit results above were recorded earlier in this same implementation;
those commands were not repeated for a fixture-only correction. Hash comparison
confirmed production, dependencies, backend, and runtime configuration unchanged.

#### Scope and integrity

- Protected tests modified: YES, only the human-authorized bootstrap setup in
  `task81-configuration.spec.ts`. The scaffold setup received the same approved
  correction. No assertion, scenario, test count, or compile-time sentinel changed.
- SHA-256 evidence:
  - configuration before approval: `c18600fce4d8d48ec2dd9a439593792802cd7a3e0364605e658625fb4ce328c1`;
  - configuration after authorized correction: `e03c9559e830ecd61f503b0a711fb9c534c0647846fff83c59b9ada3922fac17`;
  - routing unchanged: `93d80fb1cbe4598892b429f8ac49b120221f50780e95e21df760dc65198084cf`;
  - shell unchanged: `1858e4fd7c827f82b6d2721e821fd06ecafba5a462329c53b5645ea4332f5d3f`.
- No new dependencies, package/configuration changes, SSR/SSG, global store,
  Angular proxy, guards, forms, session logic, or business API services.
- Production source inspection finds exactly one Gateway URL definition and no
  internal-service target or identity-header handling. Internal URLs occur only
  as negative HTTP-test inputs.
- No backend production or migration changes. Pre-existing `.env.example`, API,
  infrastructure, Task 8.2/8.3 documentation, and unrelated output are preserved.
- This implementation updates only frontend source, frontend usage/ownership
  documentation, this task's evidence, and its roadmap status. Human
  implementation approval is recorded above; independent QA has not begun.
- Each of the 11 new files was exposed individually with `git add -N` only.
  There was no regular staging, commit, push, or merge. `git diff --cached`
  remains empty; `git diff --check` passes.

### QA report

PENDING. Human test and implementation approvals are recorded above. The final
QA agent must independently audit the original criteria, source, complete diff,
protected tests (including the explicitly approved fixture correction),
supporting tests, and evidence using `docs/definition-of-done.md`. It must account
for the documented infrastructure-blocked root check and the existing npm audit
findings. No QA verdict or `DONE` transition is issued by this implementation agent.
