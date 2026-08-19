# Roadmap

## 1. Purpose and delivery model

This roadmap is a sequence of small, behavior-driven tasks—not a license to implement an entire phase at once. Each task has one Markdown specification under `docs/tasks/`, acceptance criteria, expected tests, a human approval point, and a final QA verdict.

The mandatory sequence is defined in [development-workflow.md](development-workflow.md). Testing is part of every task from its beginning; it is not a final hardening phase.

Status meanings:

- `PLANNED`: specification exists but tests have not been requested.
- `TESTS IN REVIEW`: the test agent created the tests in Red and the tests await human approval.
- `IMPLEMENTATION IN PROGRESS`: the human approved the tests, or explicitly authorized the implementation agent to start directly from `PLANNED`.
- `QA IN REVIEW`: implementation is complete, the human approved the implementation diff, and the implementation agent exposed the diff with `git add -N` only; the independent QA audit is pending.
- `DONE`: the QA agent approved the task and the human approved the QA outcome.

## 1.1 Status transition rules

The roadmap status is a controlled state machine. Agents must validate the current status before changing it and must not silently skip a transition.

| Actor | Allowed transition | Required condition |
| --- | --- | --- |
| Test agent | `PLANNED` -> `TESTS IN REVIEW` | The specification was provided/approved, tests were created in Red, and Red evidence was recorded. |
| Implementation agent | `TESTS IN REVIEW` -> `IMPLEMENTATION IN PROGRESS` | The human approved the tests. |
| Implementation agent | `IMPLEMENTATION IN PROGRESS` -> `QA IN REVIEW` | Implementation is complete, the human approved the implementation diff, and only `git add -N` was used to expose new files. |
| Implementation agent | `PLANNED` -> `QA IN REVIEW` | The human explicitly instructed direct implementation for this task, including skipping the initial test-agent stage; implementation and diff approval requirements still apply. This is the only permitted skipped transition. |
| QA agent | `QA IN REVIEW` -> `DONE` | The independent QA audit is approved and the human approved the QA outcome. |

If the current status, evidence, or human approval does not satisfy the applicable transition, the agent must leave the status unchanged and report the divergence. No agent may invent an intermediate transition or jump to another status.

The implementation agent must never run regular `git add`, `git add .`, `git add -A`, or an equivalent staging command for this workflow. It may run only `git add -N <new-file>` to make an untracked implementation file visible to the final QA diff. Commits and other staging commands happen only after QA and the required human approval.

## 2. Phase 0 — Documentation and delivery foundation

| Task | Specification | Status |
| --- | --- | --- |
| 0.1 Document the TDD delivery workflow | `tasks/phase-00/01-tdd-workflow.md` | DONE |
| 0.2 Define test strategy and Definition of Done | `tasks/phase-00/02-quality-standards.md` | DONE |
| 0.3 Restore repository tracking and project baseline | `tasks/phase-00/03-repository-baseline.md` | DONE |

## 3. Phase 1 — Local infrastructure

| Task | Specification | Status |
| --- | --- | --- |
| 1.1 Define Docker Compose configuration and environment contract | `tasks/phase-01/01-compose-contract.md` | DONE |
| 1.2 Provision PostgreSQL with service-owned databases | `tasks/phase-01/02-postgres-databases.md` | DONE |
| 1.3 Provision RabbitMQ and validate local infrastructure | `tasks/phase-01/03-rabbitmq-and-smoke-check.md` | DONE |

## 4. Phase 2 — Backend foundations

| Task | Specification | Status |
| --- | --- | --- |
| 2.1 Create the Gradle multi-service build foundation | `tasks/phase-02/01-gradle-foundation.md` | DONE |
| 2.2 Bootstrap API Gateway and Auth Service | `tasks/phase-02/02-gateway-and-auth-skeletons.md` | DONE |
| 2.3 Bootstrap Betting and Analytics Services | `tasks/phase-02/03-betting-and-analytics-skeletons.md` | DONE |

## 5. Phase 3 — Gateway routing and boundary security

| Task | Specification | Status |
| --- | --- | --- |
| 3.1 Route public and protected API paths | `tasks/phase-03/01-gateway-routes.md` | DONE |
| 3.2 Configure CORS and external error boundaries | `tasks/phase-03/02-cors-and-errors.md` | DONE |
| 3.3 Validate JWT at the gateway boundary | `tasks/phase-03/03-gateway-jwt.md` | DONE |

## 6. Phase 4 — Auth Service MVP

| Task | Specification | Status |
| --- | --- | --- |
| 4.1 Register a user | `tasks/phase-04/01-register-user.md` | DONE |
| 4.2 Authenticate a user and issue a JWT | `tasks/phase-04/02-login-and-jwt.md` | DONE |
| 4.3 Identify the authenticated user | `tasks/phase-04/03-current-user.md` | DONE |

## 7. Phase 5 — Betting Service MVP

| Task | Specification | Status |
| --- | --- | --- |
| 5.1 Establish Bet value objects and settlement calculations | `tasks/phase-05/01-bet-domain-calculations.md` | DONE |
| 5.2 Create and retrieve a user's pending bets | `tasks/phase-05/02-create-and-read-bets.md` | DONE |
| 5.3 Update and settle a pending bet | `tasks/phase-05/03-update-and-settle-bets.md` | DONE |

## 8. Phase 6 — RabbitMQ integration

| Task | Specification | Status |
| --- | --- | --- |
| 6.1 Configure documented exchange, queue, and event envelope | `tasks/phase-06/01-messaging-contract.md` | DONE |
| 6.2 Publish betting lifecycle events after persistence | `tasks/phase-06/02-publish-betting-events.md` | DONE |
| 6.3 Consume events idempotently into analytics projections | `tasks/phase-06/03-consume-events-idempotently.md` | PLANNED |

## 9. Phase 7 — Analytics Service MVP

| Task | Specification | Status |
| --- | --- | --- |
| 7.1 Calculate dashboard summary metrics | `tasks/phase-07/01-dashboard-summary.md` | PLANNED |
| 7.2 Expose bankroll evolution | `tasks/phase-07/02-bankroll-evolution.md` | PLANNED |
| 7.3 Expose filtered performance breakdowns | `tasks/phase-07/03-performance-breakdown.md` | PLANNED |

## 10. Phase 8 — Angular frontend MVP

| Task | Specification | Status |
| --- | --- | --- |
| 8.1 Bootstrap Angular app, layout, and API boundary | `tasks/phase-08/01-angular-foundation.md` | PLANNED |
| 8.2 Implement registration and login flow | `tasks/phase-08/02-auth-flow.md` | PLANNED |
| 8.3 Implement betting and dashboard flows | `tasks/phase-08/03-betting-and-dashboard.md` | PLANNED |

## 11. Phase 9 — Cross-cutting quality hardening

| Task | Specification | Status |
| --- | --- | --- |
| 9.1 Add integration coverage for persistence and APIs | `tasks/phase-09/01-api-and-persistence-integration.md` | PLANNED |
| 9.2 Add messaging integration coverage | `tasks/phase-09/02-messaging-integration.md` | PLANNED |
| 9.3 Audit security, error handling, and critical edge cases | `tasks/phase-09/03-quality-audit.md` | PLANNED |

## 12. Phase 10 — Public documentation

| Task | Specification | Status |
| --- | --- | --- |
| 10.1 Write local setup and architecture overview | `tasks/phase-10/01-readme-setup.md` | PLANNED |
| 10.2 Document APIs, events, testing, and development flow | `tasks/phase-10/02-public-technical-docs.md` | PLANNED |

## 13. Ordering rules

- Complete tasks in their listed order unless the task explicitly declares otherwise.
- Do not begin a task's implementation before its tests are approved by the human.
- A later task may depend only on completed tasks or explicitly stated contracts.
- A cross-cutting hardening task complements, but never replaces, tests required in the individual feature tasks.
- Every new task must be added to this roadmap and created from `docs/tasks/TEMPLATE.md` before work starts.

## Expected validations

This task does not use the application TDD cycle because no application behavior is implemented.

The implementation must execute and record, when available:

```bash
docker compose config
docker compose --env-file .env.example config
git check-ignore -v .env
git diff --check
git status
git diff
```

For new untracked files, the implementation agent must make them visible in the review diff using:

```bash
git add -N <new-files>
```

This command is used only to expose file contents in `git diff`. It must not be treated as final staging approval.

The expected output is:

```text
VERDICT: APPROVED | APPROVED WITH RESERVATIONS | REJECTED

Blockers:
- ...

Important issues:
- ...

Non-blocking improvements:
- ...

Evidence:
- ...
```
