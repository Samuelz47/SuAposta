# AGENTS.md

## Project Name

Bet Control SaaS

## Project Purpose

This project is a SaaS platform for bankroll management and sports betting performance analysis.

The platform allows users to register sports bets, manage their betting history, and analyze performance through metrics such as profit, ROI, yield, win rate, drawdown, and performance by sport, league, team, market, and period.

This project is also a technical learning environment for:

- Java 21
- Spring Boot
- Angular 18
- Tailwind CSS
- Microservices
- RabbitMQ
- PostgreSQL
- Docker Compose
- Layered architecture
- Repository pattern
- Automated tests
- Codex harness
- AI coding agents
- Skills and MCP workflows
- Token-efficient development

The architecture is intentionally more complex than a simple MVP because the main objective is technical learning.

---

## Required Reading Before Coding

Before making architecture, backend, frontend, messaging, or infrastructure changes, read these files:

```text
docs/architecture.md
docs/events.md
```

When they exist, also read:

```text
docs/domain.md
docs/api-contracts.md
docs/roadmap.md
docs/testing-strategy.md
docs/definition-of-done.md
```

Do not invent architecture, event contracts, folder structure, or service responsibilities when documentation already defines them.

For an implementation task, also read its approved specification in `docs/tasks/` and only the domain/API/event documents it names as relevant.

---

## Repository Structure

The project uses a monorepo structure:

```text
bet-control-saas/
  apps/
    web/
  services/
    api-gateway/
    auth-service/
    betting-service/
    analytics-service/
  infra/
    docker/
      docker-compose.yml
      postgres/
      rabbitmq/
  docs/
    architecture.md
    events.md
    domain.md
    api-contracts.md
    roadmap.md
  AGENTS.md
  README.md
```

Main folders:

- `apps/web`: Angular frontend.
- `services/api-gateway`: API Gateway service.
- `services/auth-service`: Authentication service.
- `services/betting-service`: Betting domain service.
- `services/analytics-service`: Analytics and dashboard service.
- `infra/docker`: Local Docker infrastructure.
- `docs`: Architecture, events, API contracts, domain rules, and roadmap.

---

## Core Architecture Rules

Each backend microservice must follow layered architecture:

```text
domain
application
infrastructure
presentation
```

### Domain Layer

The domain layer contains business rules and pure domain models.

Allowed:

- Domain models
- Value objects
- Domain services
- Repository interfaces
- Domain exceptions
- Business validation

Not allowed:

- Spring annotations, unless strictly unavoidable
- JPA entities
- RabbitMQ code
- HTTP request/response DTOs
- Database-specific details
- Framework-specific logic

---

### Application Layer

The application layer contains use cases and orchestration.

Allowed:

- Application services
- Use case classes
- Commands
- Results
- Application ports
- Transaction orchestration
- Calls to domain objects
- Calls to repository interfaces
- Calls to publisher ports

Not allowed:

- HTTP controller logic
- JPA entity mapping
- RabbitMQ implementation details
- Direct SQL, unless explicitly justified
- Frontend concerns

---

### Infrastructure Layer

The infrastructure layer contains technical implementations.

Allowed:

- JPA entities
- Spring Data repositories
- Repository implementations
- RabbitMQ publishers and consumers
- Database mappers
- Configuration classes
- Security configuration
- Flyway migrations

Not allowed:

- Business rules that belong in domain
- API request/response models
- Dashboard presentation logic

---

### Presentation Layer

The presentation layer exposes APIs.

Allowed:

- REST controllers
- Request DTOs
- Response DTOs
- API mappers
- Global exception handlers
- API validations

Not allowed:

- Business rules
- Direct persistence access
- RabbitMQ publishing implementation
- Cross-service database access

Controllers must be thin.

---

## Backend Development Rules

Use:

- Java 21
- Spring Boot
- Spring Web
- Spring Security when needed
- Spring Data JPA
- PostgreSQL
- Flyway
- JUnit 5
- Mockito
- Testcontainers when integration tests need real infrastructure
- RestAssured for API tests when appropriate

General rules:

- Do not expose JPA entities directly through controllers.
- Use request and response DTOs for APIs.
- Use command/result DTOs for application services when helpful.
- Use repository interfaces in the domain or application layer.
- Implement repositories in the infrastructure layer.
- Use `BigDecimal` for money, odds, stake, profit, ROI, and yield calculations.
- Do not use `double` or `float` for monetary values.
- Do not hardcode secrets.
- Do not introduce new dependencies without explaining why.
- Do not create new microservices unless explicitly requested.
- Do not modify unrelated services.
- Do not change documented architecture without updating documentation.

---

## Service Responsibilities

### API Gateway

The API Gateway is the single entry point for frontend requests.

Allowed:

- Route requests
- Configure CORS
- Validate JWT when needed
- Apply gateway-level security

Not allowed:

- Betting business rules
- Analytics calculations
- Direct database access
- RabbitMQ betting event publishing

---

### Auth Service

The Auth Service owns authentication and user identity.

Allowed:

- User registration
- Login
- JWT generation
- Refresh token flow, if requested
- User credentials
- Roles

Not allowed:

- Betting records
- Dashboard metrics
- Analytics projections
- Accessing other service databases

---

### Betting Service

The Betting Service owns the bet lifecycle.

Allowed:

- Create bets
- Update bets
- List bets
- Filter bets
- Settle bets
- Calculate bet-level profit
- Publish betting events to RabbitMQ

Not allowed:

- Dashboard-level analytics
- Writing to analytics database
- Reading auth database directly
- Calling analytics service just to update dashboard data

The Betting Service is the source of truth for individual bets.

---

### Analytics Service

The Analytics Service owns reporting and dashboard projections.

Allowed:

- Consume betting events
- Store analytical projections
- Calculate metrics
- Expose dashboard endpoints
- Filter performance data

Not allowed:

- Owning bet lifecycle
- Modifying betting database
- Authentication logic
- Synchronous dependency on Betting Service for every dashboard query

Dashboard queries must read from `analytics_db`.

---

## RabbitMQ Rules

RabbitMQ is used for asynchronous communication from Betting Service to Analytics Service.

Before changing messaging behavior, read:

```text
docs/events.md
```

Initial exchange:

```text
betting.events
```

Initial queue:

```text
analytics.betting-events.queue
```

Initial routing keys:

```text
bet.created
bet.updated
bet.settled
```

Initial event types:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

Rules:

- Betting Service publishes betting events.
- Analytics Service consumes betting events.
- Events must use the documented envelope structure.
- Events must include `eventId`, `eventType`, `occurredAt`, `version`, `producer`, and `payload`.
- Consumers must be idempotent.
- Analytics Service should track processed events.
- Do not publish events before the related database change succeeds.
- Do not add new event types without updating `docs/events.md`.
- Do not change existing event fields without considering versioning.

The first version should not implement transactional outbox unless explicitly requested.

---

## Database Rules

Local development uses PostgreSQL through Docker Compose.

Initial strategy:

```text
One PostgreSQL container
Three databases:
- auth_db
- betting_db
- analytics_db
```

Rules:

- Each service owns its own database/schema.
- Services must not read or write another service's database.
- Use Flyway for migrations.
- Do not rely on Hibernate auto-DDL for real schema management.
- Migrations must be committed with the code change that requires them.
- Keep local development simple.

---

## Frontend Rules

The frontend uses:

- Angular 18
- Tailwind CSS
- Angular Router
- Reactive Forms
- HTTP Interceptors
- Chart.js or ApexCharts

Suggested structure:

```text
apps/web/src/app/
  core/
    auth/
    http/
    interceptors/
    layout/
  shared/
    components/
    pipes/
    utils/
  features/
    auth/
      login/
      register/
    dashboard/
      pages/
      components/
      services/
    bets/
      pages/
      components/
      services/
      models/
    settings/
```

Rules:

- Keep pages and reusable components separated.
- Use feature-based organization.
- Use services for HTTP communication.
- Use interceptors for JWT handling.
- Do not call internal backend services directly from the frontend.
- The frontend should call the API Gateway.
- Keep styling with Tailwind unless a different decision is documented.
- Avoid complex state management libraries unless explicitly requested.

---

## Testing Rules

When changing business logic, add or update tests.

Backend testing expectations:

- Unit tests for domain rules.
- Unit tests for application services.
- Integration tests for repositories when needed.
- API tests for important endpoints.
- Messaging tests when RabbitMQ behavior changes.

Use Testcontainers when tests require PostgreSQL or RabbitMQ behavior.

Frontend testing expectations:

- Component tests for important UI behavior when practical.
- Service tests for API communication when practical.
- Avoid over-testing visual-only Tailwind classes.

Do not skip tests for core business behavior.

The complete test policy is defined in `docs/testing-strategy.md` and is mandatory for every task.

---

## TDD Workflow and Agent Separation

Every code change follows this controlled sequence:

```text
Reviewed documentation
  -> small task with acceptance criteria
  -> test agent writes tests (Red)
  -> human review and approval of tests
  -> implementation agent writes production code (Green)
  -> human review of the diff
  -> final QA agent audits evidence and scope
  -> corrections, relevant full suite, commit and PR
```

Responsibilities are intentionally separate:

- The human and documentation define **what the system must do**.
- The test agent defines **how that behavior is proven**. It receives the task, relevant documents, testing standards, and current structure, but no proposed implementation.
- The implementation agent makes the approved behavior exist. It receives the task, approved tests, architecture, and current code.
- The QA agent independently audits **where the result can be wrong or incomplete**. It receives the original task, acceptance criteria, documents, tests, and diff; it must inspect evidence rather than rely on an implementation self-assessment.

Use separate, focused sessions/contexts for these roles. Do not pass hidden chain-of-thought, implementation plans, or defensive claims from one role to the next.

### Protected approved tests

Tests approved by the human are a behavioral contract. The implementation agent must not remove, ignore, weaken, or alter them merely to make the suite pass.

This includes, but is not limited to:

- replacing a specific assertion with a generic assertion;
- removing a boundary or negative scenario;
- adding `@Disabled` or equivalent skipping behavior;
- catching an exception without asserting it;
- adding mocks to conceal a real defect;
- changing an expected value to match the current implementation; or
- reducing an integration test to a unit test only to simplify implementation.

If an approved test appears incorrect, the implementation agent must:

1. Stop implementation of the affected behavior.
2. Explain the inconsistency with evidence.
3. Identify the affected business rule or contract.
4. Wait for human review and an explicit test/specification decision.

Only the human may approve a change to an approved test. The reason and affected acceptance criterion must be recorded in the task file.

### Required QA audit

The QA agent must not approve a task merely because a test command is green. It must audit the requirements, tests, code, security, architecture, and scope according to `docs/definition-of-done.md` and use the standard verdict format there.

---

## Documentation Rules

When changing architecture, events, APIs, or domain rules, update the related documentation.

Documentation files:

```text
docs/architecture.md
docs/events.md
docs/domain.md
docs/api-contracts.md
docs/roadmap.md
```

Rules:

- Update `docs/architecture.md` when service responsibilities, folder structure, database strategy, or major architecture decisions change.
- Update `docs/events.md` when RabbitMQ topology, routing keys, event types, envelope structure, or payloads change.
- Update `docs/domain.md` when business rules change.
- Update `docs/api-contracts.md` when endpoints, request DTOs, response DTOs, or status codes change.
- Update `docs/roadmap.md` when scope or milestones change.

---

## Token-Efficient Codex Workflow

Agents must avoid unnecessary repository scanning.

Before making changes:

1. Identify the requested scope.
2. Read only the relevant docs and files.
3. List the files likely to be changed.
4. Make small, focused changes.
5. Run only relevant tests when possible.
6. Summarize what changed.

Avoid:

- Reading the entire repository without need.
- Editing unrelated files.
- Refactoring large areas without request.
- Adding dependencies casually.
- Rewriting working code for style only.
- Creating abstractions before they are needed.

Preferred task style:

```text
Implement only POST /bets in betting-service.
Read docs/architecture.md and docs/events.md.
Do not change frontend, gateway, auth-service, or analytics-service.
Create or update tests only for this endpoint and related domain/application logic.
Before editing, list the files you intend to change.
```

---

## Change Scope Rules

When asked to work on one service, stay inside that service unless another file is clearly required.

Examples:

- A Betting Service endpoint should not change Angular unless explicitly requested.
- A RabbitMQ event payload change must update `docs/events.md`.
- A database field change must update Flyway migrations.
- A frontend dashboard change should not change backend contracts unless explicitly requested.
- An analytics calculation change should not modify Betting Service unless the event contract or bet lifecycle requires it.

---

## Naming Rules

Use clear, explicit names.

Backend examples:

```text
CreateBetService
UpdateBetService
SettleBetService
ListBetsService
BetRepository
BetEventPublisher
RabbitBetEventPublisher
BetCreatedPayload
BetSettledPayload
```

Avoid vague names:

```text
Manager
Processor
Handler
Helper
Util
ServiceImpl
```

Use `Handler` only when there is a clear message/event handling responsibility.

---

## Error Handling Rules

Backend services should use consistent error handling.

Rules:

- Use domain exceptions for business rule violations.
- Use global exception handlers in the presentation layer.
- Return appropriate HTTP status codes.
- Do not leak internal stack traces in API responses.
- Validate request DTOs.
- Log important technical failures.
- Include enough context in logs to debug issues.

---

## Security Rules

General rules:

- Do not hardcode secrets.
- Do not commit real credentials.
- Use environment variables for infrastructure credentials.
- JWT validation should be centralized where appropriate.
- Do not include passwords, tokens, or sensitive personal information in events.
- Do not expose user data unnecessarily across services.

---

## Initial MVP Scope

The first MVP should focus on:

- Docker Compose with PostgreSQL and RabbitMQ.
- API Gateway running.
- Auth Service with register and login.
- Betting Service with create, list, update, and settle bet.
- Betting Service publishing RabbitMQ events.
- Analytics Service consuming RabbitMQ events.
- Analytics Service exposing dashboard metrics.
- Angular app with login, bets page, and dashboard.
- Basic tests for backend services.

---

## Out of Scope Unless Explicitly Requested

Do not implement these features unless explicitly requested:

- Payment plans
- Subscription billing
- Multi-tenant organizations
- External sportsbook integrations
- Automatic odds import
- AI betting recommendations
- Team catalog service
- Sport catalog service
- Kubernetes
- Cloud deployment
- Event sourcing
- Complex CQRS
- Distributed tracing
- Full observability stack
- Transactional outbox
- Dead-letter queues
- Event replay

---

## Agent Behavior

When working as an AI coding agent:

- Be explicit about assumptions.
- Prefer small, reviewable changes.
- Ask for clarification only when truly blocked.
- If the request is clear, proceed with the best reasonable implementation.
- Do not silently change architecture.
- Do not invent undocumented conventions.
- Keep the project beginner-friendly enough for learning.
- Favor maintainability over cleverness.
- Favor clear code over excessive abstraction.
- Explain important decisions in the final summary.

---

## Final Response Expectations

After making changes, summarize:

- What was changed.
- Which files were changed.
- Which tests were added or updated.
- Which tests were run.
- Any known limitations.
- Any recommended next steps.

If tests were not run, explain why.
