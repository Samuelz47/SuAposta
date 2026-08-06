# Roadmap

## 1. Purpose and delivery model

This roadmap is a sequence of small, behavior-driven tasks—not a license to implement an entire phase at once. Each task has one Markdown specification under `docs/tasks/`, acceptance criteria, expected tests, a human approval point, and a final QA verdict.

The mandatory sequence is defined in [development-workflow.md](development-workflow.md). Testing is part of every task from its beginning; it is not a final hardening phase.

Status meanings:

- `PLANNED`: specification exists but tests have not been requested.
- `TESTS IN REVIEW`: tests exist in Red and await human approval.
- `IMPLEMENTATION IN PROGRESS`: approved tests exist; production code may be changed.
- `QA IN REVIEW`: implementation diff awaits independent audit.
- `DONE`: all Definition of Done requirements were met, including human review and QA approval.

## 2. Phase 0 — Documentation and delivery foundation

| Task | Specification | Status |
| --- | --- | --- |
| 0.1 Document the TDD delivery workflow | `tasks/phase-00/01-tdd-workflow.md` | DONE |
| 0.2 Define test strategy and Definition of Done | `tasks/phase-00/02-quality-standards.md` | DONE |
| 0.3 Restore repository tracking and project baseline | `tasks/phase-00/03-repository-baseline.md` | PLANNED |

## 3. Phase 1 — Local infrastructure

| Task | Specification | Status |
| --- | --- | --- |
| 1.1 Define Docker Compose configuration and environment contract | `tasks/phase-01/01-compose-contract.md` | PLANNED |
| 1.2 Provision PostgreSQL with service-owned databases | `tasks/phase-01/02-postgres-databases.md` | PLANNED |
| 1.3 Provision RabbitMQ and validate local infrastructure | `tasks/phase-01/03-rabbitmq-and-smoke-check.md` | PLANNED |

## 4. Phase 2 — Backend foundations

| Task | Specification | Status |
| --- | --- | --- |
| 2.1 Create the Gradle multi-service build foundation | `tasks/phase-02/01-gradle-foundation.md` | PLANNED |
| 2.2 Bootstrap API Gateway and Auth Service | `tasks/phase-02/02-gateway-and-auth-skeletons.md` | PLANNED |
| 2.3 Bootstrap Betting and Analytics Services | `tasks/phase-02/03-betting-and-analytics-skeletons.md` | PLANNED |

## 5. Phase 3 — Gateway routing and boundary security

| Task | Specification | Status |
| --- | --- | --- |
| 3.1 Route public and protected API paths | `tasks/phase-03/01-gateway-routes.md` | PLANNED |
| 3.2 Configure CORS and external error boundaries | `tasks/phase-03/02-cors-and-errors.md` | PLANNED |
| 3.3 Validate JWT at the gateway boundary | `tasks/phase-03/03-gateway-jwt.md` | PLANNED |

## 6. Phase 4 — Auth Service MVP

| Task | Specification | Status |
| --- | --- | --- |
| 4.1 Register a user | `tasks/phase-04/01-register-user.md` | PLANNED |
| 4.2 Authenticate a user and issue a JWT | `tasks/phase-04/02-login-and-jwt.md` | PLANNED |
| 4.3 Identify the authenticated user | `tasks/phase-04/03-current-user.md` | PLANNED |

## 7. Phase 5 — Betting Service MVP

| Task | Specification | Status |
| --- | --- | --- |
| 5.1 Establish Bet value objects and settlement calculations | `tasks/phase-05/01-bet-domain-calculations.md` | PLANNED |
| 5.2 Create and retrieve a user's pending bets | `tasks/phase-05/02-create-and-read-bets.md` | PLANNED |
| 5.3 Update and settle a pending bet | `tasks/phase-05/03-update-and-settle-bets.md` | PLANNED |

## 8. Phase 6 — RabbitMQ integration

| Task | Specification | Status |
| --- | --- | --- |
| 6.1 Configure documented exchange, queue, and event envelope | `tasks/phase-06/01-messaging-contract.md` | PLANNED |
| 6.2 Publish betting lifecycle events after persistence | `tasks/phase-06/02-publish-betting-events.md` | PLANNED |
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
