# 2.3 — Bootstrap Betting and Analytics Services

## Context

Betting owns bet lifecycle; Analytics owns projections and reports.

## Objective

Create runnable, layered skeletons for Betting and Analytics Services.

## Acceptance criteria

- [x] Both applications start on documented ports.
- [x] Each uses the four documented layers.
- [x] Health behavior is tested without domain features.

## Boundary and negative cases

- [x] Analytics has no bet-lifecycle write behavior.

## Out of scope

- Bet endpoints, messaging, projections, and metrics.

## Dependencies

- Task 2.1.

## Expected tests

- Application context/health integration tests.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.

Current status: `DONE`

### Red evidence

- Command: `./gradlew :services:betting-service:test :services:analytics-service:test --no-daemon`
- Test compilation succeeded for both services; the test task execution failed with 7 failing tests.
- Betting context and health tests fail because no Spring Boot application class exists yet for the Betting Service.
- Analytics context, health, and negative-boundary tests fail because no Spring Boot application class exists yet for the Analytics Service.
- Betting and Analytics layer-structure tests fail because `src/main/java` does not exist yet in either service.
- The failures reflect the missing Task 2.3 skeletons, not a Gradle compilation failure, missing PostgreSQL, RabbitMQ, or another service.

### Green evidence

- Command: `./gradlew :services:betting-service:test :services:analytics-service:test --no-daemon` — all approved tests passed; both contexts loaded, both health endpoints responded on ports 8082 and 8083, all four layers were found, and `POST /bets` returned `404` in Analytics.
- Command: `./gradlew check --no-daemon` — passed for all four backend modules, including Java 21 verification.

| Gate | Decision / evidence | Date / approver |
| --- | --- | --- |
| Specification provided | Task specification and acceptance criteria were supplied and approved for implementation in this request. | 2026-08-07 / human |
| Tests in Red | `./gradlew :services:betting-service:test :services:analytics-service:test --no-daemon` — 7 tests failed for the missing skeleton behavior described above. | 2026-08-07 / test agent |
| Tests approved | The implementation request identifies the Red tests as approved and authorizes implementation without changing them. | 2026-08-07 / human |
| Implementation in Green | Focused Task 2.3 tests and the root Gradle `check` suite passed without changing approved tests. | 2026-08-07 / implementation agent |
| Human diff review | Approved by the human after implementation and test review. | 2026-08-07 / human |
| QA verdict | APPROVED; human approval recorded below. | 2026-08-07 / QA agent + human |

### QA report

VERDICT: APPROVED

Blockers:
- None for the implemented runtime behavior.

Important issues:
- None.

Non-blocking improvements:
- The layer-structure tests currently collect directory basenames from the whole Java source tree. They could assert the exact service package paths (`com/suaposta/betting/...` and `com/suaposta/analytics/...`) to make false positives less likely. The current production package layout itself is correct.

Evidence:
- Inspected the task specification and `docs/architecture.md`, `docs/events.md`, `docs/domain.md`, `docs/api-contracts.md`, `docs/roadmap.md`, `docs/testing-strategy.md`, and `docs/definition-of-done.md`.
- `./gradlew :services:betting-service:test :services:analytics-service:test --rerun-tasks --no-daemon` passed; all 7 focused tests passed (3 Betting, 4 Analytics).
- `./gradlew check --rerun-tasks --no-daemon` passed; all four backend modules compiled, tested, and passed Java 21 verification.
- `docker compose --env-file .env.example config` passed. `docker compose config` could not resolve the required `POSTGRES_HOST_PORT` because the local ignored `.env` file is absent; this is unrelated to Task 2.3.
- `git diff --check` passed, and the new service files contain no trailing whitespace or secrets.
- The implementation agent exposed all new files with `git add -N`; `git diff --cached --stat` is empty, so no file content was staged.
- Betting and Analytics expose Spring Boot applications on documented ports 8082 and 8083, define the four documented layers, expose only health behavior, and Analytics returns 404 for `POST /bets`.
- No bet endpoints, messaging, projections, metrics, database access, or unrelated service changes were introduced.

### Human QA approval

- 2026-08-07: Human approved the QA verdict; Task 2.3 is complete.

### Implementation follow-up

- 2026-08-07: the implementation agent ran `git add -N` for all new Betting and Analytics implementation and approved-test files. `git status` now exposes them as intent-to-add (` A`), while `git diff --cached --stat` remains empty; no file content was staged.
- The non-blocking suggestion to strengthen the layer-structure tests was not implemented because those tests are approved and the change is outside Task 2.3's minimum skeleton scope.
