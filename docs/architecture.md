# Architecture

## 1. Project Overview

This project is a SaaS platform for bankroll management and sports betting performance analysis.

The application allows users to register sports bets, track bankroll evolution, and analyze performance through advanced metrics such as:

- Profit
- ROI
- Yield
- Win rate
- Drawdown
- Performance by sport
- Performance by league
- Performance by team
- Performance by market
- Performance by period

The project is intentionally designed with microservices, asynchronous messaging, Docker infrastructure, and separated databases as a learning environment for working with AI coding agents, Codex harness, skills, MCPs, multi-agent workflows, and token-efficient development.

Although the architecture is more complex than strictly necessary for an MVP, the goal is to create a realistic technical playground for learning modern backend, frontend, infrastructure, testing, and AI-assisted coding practices.

---

## 2. Main Goals

The system must support:

- User registration and authentication.
- JWT-based access control.
- Sports bet creation, listing, updating, and settlement.
- Event publishing when important betting actions happen.
- Asynchronous analytics processing through RabbitMQ.
- Dashboard generation based on betting performance.
- Filtering metrics by period, sport, league, team, market, status, odds, and stake.
- Local development using Docker Compose.
- Independent microservices with clear responsibilities.
- Clean layered architecture inside each service.

---

## 3. Technology Stack

### Backend

- Java 21
- Spring Boot
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- RabbitMQ
- Docker Compose
- JUnit 5
- Mockito
- Testcontainers
- RestAssured

### Frontend

- Angular 18
- Tailwind CSS
- Angular Router
- Reactive Forms
- HTTP Interceptors
- Chart.js or ApexCharts

### Infrastructure

- Docker Compose
- PostgreSQL
- RabbitMQ
- RabbitMQ Management UI
- Optional PgAdmin

---

## 4. Repository Strategy

The project should use a monorepo.

Recommended structure:

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
    development-workflow.md
    testing-strategy.md
    definition-of-done.md
    tasks/
  AGENTS.md
  README.md
```

Reasoning:

- `apps/` contains user-facing applications.
- `services/` contains backend microservices.
- `infra/` contains local infrastructure configuration.
- `docs/` contains the source of truth for architecture, events, APIs, and product decisions.
- `docs/tasks/` contains the small, reviewable behavioral specifications that drive implementation.
- `AGENTS.md` contains Codex-specific project instructions.

---

## 5. Microservices

The backend is divided into four main services.

---

## 5.1 API Gateway

### Responsibility

The API Gateway is the single entry point for frontend requests.

It should route requests to the appropriate backend services and centralize cross-cutting entry concerns.

### Main responsibilities

- Route HTTP requests to backend services.
- Validate JWT tokens when needed.
- Configure CORS.
- Provide a single public API entry point for the frontend.
- Avoid business logic.

### Suggested technology

- Java 21
- Spring Boot
- Spring Cloud Gateway
- Spring Security

### Should not do

- It must not contain betting business rules.
- It must not calculate analytics.
- It must not access service databases directly.
- It must not publish betting domain events.

---

## 5.2 Auth Service

### Responsibility

The Auth Service manages users and authentication.

### Main responsibilities

- Register users.
- Authenticate users.
- Generate JWT access tokens.
- Optionally generate refresh tokens.
- Manage user credentials.
- Manage user roles.
- Expose user identity data needed by other services.

### Initial domain concepts

- User
- Role
- RefreshToken, optional

### Database

The Auth Service owns its own database/schema.

Suggested database name:

```text
auth_db
```

### Should not do

- It must not store bets.
- It must not calculate betting performance.
- It must not access betting or analytics databases.

---

## 5.3 Betting Service

### Responsibility

The Betting Service is responsible for the operational betting domain.

It receives, stores, updates, and settles user bets.

### Main responsibilities

- Create bets.
- Update bets.
- List bets.
- Filter bets.
- Settle bets.
- Calculate bet-level profit.
- Publish betting events to RabbitMQ.
- Maintain the source of truth for individual bets.

### Initial domain concepts

- Bet
- BetStatus
- Sport
- Market
- Stake
- Odds
- Profit

### Initial bet statuses

```text
PENDING
WON
LOST
VOID
CASHOUT
CANCELLED
```

### Database

The Betting Service owns its own database/schema.

Suggested database name:

```text
betting_db
```

### Important rule

The Betting Service owns the canonical state of a bet.

The Analytics Service may store a copy/projection of betting data for reporting, but the original bet lifecycle belongs to the Betting Service.

### Should not do

- It must not calculate dashboard-level analytics.
- It must not write directly to the analytics database.
- It must not access auth database tables.
- It must not call the Analytics Service directly just to update dashboard data.

---

## 5.4 Analytics Service

### Responsibility

The Analytics Service is responsible for performance metrics, reporting, and dashboard data.

It consumes betting events from RabbitMQ and builds analytical views based on betting activity.

### Main responsibilities

- Consume betting events.
- Store analytical projections.
- Calculate dashboard metrics.
- Provide performance filters.
- Provide historical bankroll data.
- Provide aggregated reports.

### Initial metrics

- Total stake
- Total profit
- ROI
- Yield
- Win rate
- Average odds
- Bets count
- Won bets
- Lost bets
- Void bets
- Cashout bets
- Cancelled bets
- Current drawdown
- Maximum drawdown

### Initial filters

- Period
- Sport
- League
- Team
- Market
- Status
- Minimum odds
- Maximum odds
- Minimum stake
- Maximum stake

### Database

The Analytics Service owns its own database/schema.

Suggested database name:

```text
analytics_db
```

### Initial table strategy

For the first version, the Analytics Service may start with one main projection table:

```text
analytics_bets
```

Later, it may evolve to pre-aggregated tables such as:

```text
daily_performance
sport_performance
league_performance
market_performance
bankroll_history
```

### Should not do

- It must not be the source of truth for bet lifecycle.
- It must not modify bets in the Betting Service database.
- It must not depend on synchronous calls to the Betting Service for every dashboard query.
- It must not own authentication logic.

---

## 6. Internal Architecture Pattern

Each backend microservice should follow layered architecture.

Base layers:

```text
domain
application
infrastructure
presentation
```

---

## 6.1 Domain Layer

The domain layer contains pure business concepts and rules.

It must not depend on Spring, JPA, RabbitMQ, HTTP, or database details.

Typical contents:

```text
domain/
  model/
  repository/
  exception/
  service/
```

Examples:

```text
Bet
BetStatus
Odds
Stake
ProfitCalculator
BetRepository
InvalidBetStatusException
```

Rules that belong in the domain:

- A pending bet may be settled as WON, LOST, VOID, CASHOUT, or CANCELLED.
- A settled bet should not be settled again unless an explicit correction flow exists.
- A won bet has positive profit.
- A lost bet has negative profit.
- A void bet has zero profit.
- A cashout bet may have custom profit.
- Odds must be greater than 1.
- Stake must be greater than 0.

---

## 6.2 Application Layer

The application layer contains use cases and orchestration logic.

It coordinates repositories, domain rules, transactions, and event publishing.

Typical contents:

```text
application/
  service/
  usecase/
  dto/
  port/
```

Examples:

```text
CreateBetService
UpdateBetService
SettleBetService
ListBetsService
GetDashboardMetricsService
BetEventPublisher
CreateBetCommand
BetResult
```

Responsibilities:

- Receive input commands.
- Validate application-level requirements.
- Load domain objects.
- Call domain behavior.
- Save changes through repository interfaces.
- Publish domain/application events through ports.
- Return output DTOs.

The application layer may depend on domain interfaces and application ports, but should not depend directly on JPA entities, RabbitMQ templates, or HTTP request objects.

---

## 6.3 Infrastructure Layer

The infrastructure layer contains technical implementations.

Typical contents:

```text
infrastructure/
  persistence/
  messaging/
  security/
  config/
```

Examples:

```text
JpaBetRepository
BetRepositoryImpl
BetEntity
BetPersistenceMapper
RabbitBetEventPublisher
RabbitConfig
SecurityConfig
Flyway migrations
```

Responsibilities:

- Implement repository interfaces.
- Map domain models to persistence entities.
- Configure database access.
- Configure RabbitMQ.
- Implement event publishers and consumers.
- Implement framework-specific concerns.

---

## 6.4 Presentation Layer

The presentation layer exposes APIs to external clients.

Typical contents:

```text
presentation/
  controller/
  dto/
  mapper/
  exception/
```

Examples:

```text
BetController
CreateBetRequest
SettleBetRequest
BetResponse
BetApiMapper
GlobalExceptionHandler
```

Responsibilities:

- Receive HTTP requests.
- Validate request DTOs.
- Call application services.
- Convert application results to API responses.
- Return proper HTTP status codes.
- Handle API-level errors.

Controllers must not contain business logic.

---

## 7. Betting Service Suggested Package Structure

```text
services/betting-service/
  src/main/java/com/betcontrol/betting/
    BettingServiceApplication.java

    domain/
      model/
        Bet.java
        BetStatus.java
        Market.java
        Sport.java
      repository/
        BetRepository.java
      service/
        ProfitCalculator.java
      exception/
        BetNotFoundException.java
        InvalidBetStatusException.java

    application/
      service/
        CreateBetService.java
        UpdateBetService.java
        SettleBetService.java
        ListBetsService.java
      dto/
        CreateBetCommand.java
        UpdateBetCommand.java
        SettleBetCommand.java
        BetResult.java
      port/
        BetEventPublisher.java

    infrastructure/
      persistence/
        entity/
          BetEntity.java
        repository/
          SpringDataBetRepository.java
          BetRepositoryImpl.java
        mapper/
          BetPersistenceMapper.java
      messaging/
        publisher/
          RabbitBetEventPublisher.java
        event/
          BettingEventEnvelope.java
          BetCreatedPayload.java
          BetUpdatedPayload.java
          BetSettledPayload.java
      config/
        RabbitConfig.java
        JpaConfig.java

    presentation/
      controller/
        BetController.java
      dto/
        CreateBetRequest.java
        UpdateBetRequest.java
        SettleBetRequest.java
        BetResponse.java
      mapper/
        BetApiMapper.java
      exception/
        GlobalExceptionHandler.java
```

---

## 8. Data Ownership

Each service owns its own data.

Rules:

- Auth Service owns user identity data.
- Betting Service owns bet lifecycle data.
- Analytics Service owns reporting projections.
- API Gateway owns no business data.

A service must not read or write another service's database directly.

Communication between services should happen through:

- REST, when synchronous response is required.
- RabbitMQ, when asynchronous propagation is enough.

---

## 9. Database Strategy

For local development, use PostgreSQL through Docker Compose.

Initial recommendation:

```text
One PostgreSQL container
Three databases:
- auth_db
- betting_db
- analytics_db
```

This keeps local infrastructure simple while preserving logical database separation.

Future option:

```text
Three PostgreSQL containers:
- auth-postgres
- betting-postgres
- analytics-postgres
```

This is more realistic, but heavier for local development.

The first version should use one PostgreSQL container with separate databases.

---

## 10. Messaging Strategy

RabbitMQ is used for asynchronous communication.

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

Recommended first version:

- Betting Service publishes events.
- Analytics Service consumes all betting events from a single queue.
- Analytics Service updates its own projection table.

This keeps the learning curve manageable.

---

## 11. Synchronous Communication

The frontend communicates with backend services through the API Gateway.

Suggested external API flow:

```text
Angular app
  ↓
API Gateway
  ↓
Auth Service / Betting Service / Analytics Service
```

The frontend should not call internal services directly.

---

## 12. Asynchronous Communication

Betting events should flow through RabbitMQ.

Example flow:

```text
User creates bet
  ↓
Frontend sends POST /bets
  ↓
API Gateway routes request
  ↓
Betting Service creates bet
  ↓
Betting Service saves bet in betting_db
  ↓
Betting Service publishes BetCreatedEvent
  ↓
RabbitMQ routes event
  ↓
Analytics Service consumes event
  ↓
Analytics Service updates analytics_db
```

---

## 13. Frontend Architecture

The Angular app should use a simple feature-based structure.

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

Initial pages:

```text
/login
/register
/dashboard
/bets
/bets/new
/bets/:id
/settings
```

Initial dashboard components:

```text
MetricCard
BankrollEvolutionChart
ProfitChart
DrawdownChart
BetFilters
RecentBetsTable
```

Initial bets components:

```text
BetTable
BetForm
BetStatusBadge
BetFilters
SettleBetModal
```

---

## 14. Dashboard MVP

The first dashboard version should include:

Metric cards:

- Current bankroll
- Net profit
- ROI
- Yield
- Total stake
- Win rate

Charts:

- Bankroll evolution
- Profit over time
- Drawdown over time

Tables:

- Recent bets
- Best leagues
- Worst leagues

Filters:

- Date range
- Sport
- League
- Market
- Status

---

## 15. Calculation Rules

### Profit

For WON bets:

```text
profit = stake * (odds - 1)
```

For LOST bets:

```text
profit = -stake
```

For VOID bets:

```text
profit = 0
```

For CASHOUT bets:

```text
profit = custom cashout result
```

For PENDING bets:

```text
profit = null
```

Dashboard aggregation never converts pending profit into a financial result. Pending rows are excluded from financial, rate, average-odds, and drawdown calculations.

---

### Return Amount

For WON bets:

```text
returnAmount = stake * odds
```

For LOST bets:

```text
returnAmount = 0
```

For VOID bets:

```text
returnAmount = stake
```

For CASHOUT bets:

```text
returnAmount = stake + profit
```

---

### ROI

```text
ROI = (totalProfit / totalStake) * 100
```

For dashboard aggregation, `totalProfit` and `totalStake` include only `WON`, `LOST`, and `CASHOUT` rows.

---

### Yield

```text
Yield = (totalProfit / totalStake) * 100
```

For this project, ROI and Yield may initially use the same formula.

For the first version, they do use the same formula and status eligibility. Their percentage scale is `2` with `HALF_UP`.

Later, they may be separated if the product defines ROI based on bankroll allocation and Yield based only on betting turnover.

---

### Win Rate

```text
Win Rate = (wonBets / totalResolvedBets) * 100
```

VOID and CANCELLED bets should not count as wins or losses.

`CASHOUT` and `PENDING` also do not count as wins or losses. The dashboard denominator contains only `WON` and `LOST` rows.

---

### Drawdown

Drawdown measures the decline from a previous peak of cumulative profit.

The first dashboard version uses a zero baseline because initial bankroll is not yet part of Task 7.1.

Basic algorithm:

```text
currentProfit = 0
peak = 0
maxDrawdown = 0

for each WON, LOST, or CASHOUT bet ordered by settledAt, then betId:
    currentProfit += profit

    if currentProfit > peak:
        peak = currentProfit

    drawdown = peak - currentProfit

    if drawdown > maxDrawdown:
        maxDrawdown = drawdown

currentDrawdown = peak - currentProfit
```

Task 7.1 exposes absolute money drawdown only. The complete status matrix, average-odds formula, count rules, scales, rounding, and zero-denominator behavior are canonical in `docs/domain.md`.

---

## 16. Initial MVP Scope

The first technical MVP should include:

- Local Docker Compose with PostgreSQL and RabbitMQ.
- API Gateway running.
- Auth Service with register and login.
- Betting Service with create, list, update, and settle bet.
- Betting Service publishing events.
- Analytics Service consuming events.
- Analytics Service exposing dashboard metrics.
- Angular app with login, bets page, and dashboard.
- Basic tests for backend services.

---

## 17. Out of Scope for First Version

The following features should not be implemented in the first version unless explicitly requested:

- Payment plans.
- Subscription billing.
- Multi-tenant organization accounts.
- External sportsbook integrations.
- Automatic odds import.
- AI betting recommendations.
- Team catalog service.
- Sport catalog service.
- Complex admin panel.
- Mobile app.
- Kubernetes.
- Cloud deployment.
- Event sourcing.
- CQRS with complex projections.
- Distributed tracing.
- Advanced observability stack.

---

## 18. Architectural Decisions

### Decision 1: Use microservices despite MVP complexity

Reason:

The main goal is technical learning, not speed of product delivery.

---

### Decision 2: Use PostgreSQL through Docker Compose

Reason:

PostgreSQL is free, production-grade, easy to run locally, and works well with Spring Boot.

---

### Decision 3: Start with one PostgreSQL container and multiple databases

Reason:

This preserves service data separation without increasing local infrastructure complexity too much.

---

### Decision 4: Use RabbitMQ for betting events

Reason:

RabbitMQ provides a practical way to learn asynchronous communication, queues, exchanges, routing keys, producers, and consumers.

---

### Decision 5: Start analytics with one projection table

Reason:

This avoids premature optimization and makes the first version easier to understand.

---

### Decision 6: Keep sport, league, team, and market simple at first

Reason:

The first version should not create a catalog microservice or heavy normalization.

Initial representation may use strings or enums. Normalization can happen later.

---

### Decision 7: Use layered architecture inside each service

Reason:

The developer is already comfortable with repositories, services, and layered architecture. This keeps the learning curve focused on microservices, messaging, frontend, Docker, and AI-assisted workflows.

---

## 19. Development Guidelines for AI Agents

AI agents working on this project must:

- Read this file before making architecture-related changes.
- Avoid changing unrelated services.
- Keep changes small and scoped.
- Follow the existing layered architecture.
- Avoid adding dependencies without justification.
- Add or update tests when changing business logic.
- Avoid creating new microservices unless explicitly requested.
- Avoid bypassing RabbitMQ for analytics updates.
- Avoid direct database access across services.
- Preserve documented event contracts.
- Update documentation when changing architecture, events, or APIs.
