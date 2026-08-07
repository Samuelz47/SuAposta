# 2.1 — Create the Gradle multi-service build foundation

## Context

Java services must use Java 21 and remain independently buildable.

## Objective

Create the root build conventions and independent service module structure.

## Acceptance criteria

- [ ] Each backend service is a Java 21 buildable module.
- [ ] Tests can run per service without starting unrelated services.
- [ ] No business behavior is implemented.

## Boundary and negative cases

- [ ] A module configured with a non-Java-21 toolchain fails clearly.

## Out of scope

- HTTP endpoints, persistence, and security.

## Dependencies

- Phase 1.

## Expected tests

- Build/toolchain verification and minimal test execution.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Current status: `DONE`.

| Current status | Pending gate |
| --- | --- |
| DONE | None. |

| Gate | Decision / evidence | Date / approver |
| --- | --- | --- |
| Specification provided | Approved by the implementation request in this task | 2026-08-06 / human |
| Tests in Red | Not applicable: pure build foundation with no application behavior | 2026-08-06 / implementation |
| Tests approved | Not applicable: no application tests were created | 2026-08-06 / implementation |
| Implementation in Green | `./gradlew test` and per-service `check` passed; Java 17 negative validation failed clearly | 2026-08-06 / implementation |
| Human diff review | Approved | 2026-08-06 / human |
| QA verdict | APPROVED | 2026-08-06 / QA + human |

Validation evidence:

- `./gradlew test --no-daemon`: successful; all service test tasks were `NO-SOURCE` because no behavior exists yet.
- `./gradlew :services:api-gateway:test :services:auth-service:test :services:betting-service:test :services:analytics-service:test --no-daemon`: successful.
- `./gradlew :services:api-gateway:check :services:auth-service:check :services:betting-service:check :services:analytics-service:check --no-daemon`: successful, including `verifyJava21` for every service.
- `./gradlew :services:api-gateway:check --no-daemon -PjavaVersion=17`: failed as required with `requires a Java 21 toolchain, but 17 was configured`.
- `docker compose config`: rejected missing required environment variables, as expected without `.env`.
- `docker compose --env-file .env.example config`: successful.
- `git check-ignore -v .env`: confirmed `.env` is ignored.
- `git diff --check`: successful.
- New files were exposed with `git add -N` only; no regular staging, commit, push, or merge was performed.

### Approved-test changes

None.

### QA report

VERDICT: APPROVED

Blockers:
- None.

Important issues:
- None.

Non-blocking improvements:
- Test tasks are `NO-SOURCE`, as expected for a build foundation with no application behavior.

Evidence:

- `./gradlew test --no-daemon`: successful.
- Per-service `check` passed independently for API Gateway, Auth Service, Betting Service, and Analytics Service.
- `./gradlew :services:api-gateway:check --no-daemon -PjavaVersion=17`: failed clearly with the required Java 21 toolchain message.
- `docker compose --env-file .env.example config`: successful.
- `git diff --check`: successful.
- Diff restricted to the Gradle foundation and its task/roadmap documentation.
- No regular staging, commit, push, or merge was performed.
- Human approval of the QA outcome: 2026-08-06.
