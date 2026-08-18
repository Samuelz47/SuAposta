# 6.1 — Configure documented exchange, queue, and event envelope

## Context

`docs/events.md` is the source of truth for the initial RabbitMQ topology, routing keys, event names, envelope structure, payload shapes, serialization rules, and version-one event contract.

This task establishes the messaging contract and RabbitMQ topology required by the later publisher and consumer tasks.

Task 6.1 must not publish Bet lifecycle events from application use cases and must not consume events into Analytics projections.

The initial asynchronous flow is:

```text
Betting Service
  ↓
betting.events
  ↓
analytics.betting-events.queue
  ↓
Analytics Service
```

The initial lifecycle event types are:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

with routing keys:

```text
bet.created
bet.updated
bet.settled
```

## Objective

Configure the version-one RabbitMQ topology and define a stable, serializable event contract shared by the initial betting lifecycle events.

The result of this task must provide:

- the documented topic exchange;
- the documented Analytics queue;
- the three documented bindings;
- stable messaging constants;
- a version-one event envelope;
- explicit payload contracts for the initial event types;
- JSON serialization and deserialization compatible with `docs/events.md`;
- exact decimal handling using `BigDecimal`.

No application lifecycle operation should publish messages yet.

## RabbitMQ topology

### Exchange

The initial exchange is:

```text
betting.events
```

Exchange type:

```text
topic
```

Only this exchange is part of the initial contract.

Task 6.1 must not introduce additional betting exchanges.

### Queue

The initial Analytics queue is:

```text
analytics.betting-events.queue
```

All initial betting lifecycle events consumed by Analytics use this queue.

Task 6.1 must not create separate queues per event type.

### Routing keys

The initial routing keys are exactly:

```text
bet.created
bet.updated
bet.settled
```

They map to event types as follows:

| Event type | Routing key |
| --- | --- |
| `BET_CREATED` | `bet.created` |
| `BET_UPDATED` | `bet.updated` |
| `BET_SETTLED` | `bet.settled` |

Future routing keys such as:

```text
bet.cancelled
bet.deleted
bet.corrected
```

must not be implemented by this task.

`CANCELLED` is a settlement status and, when lifecycle publishing is implemented in Task 6.2, belongs to:

```text
BET_SETTLED
bet.settled
```

It must not introduce a `bet.cancelled` event in the initial version.

### Bindings

The queue:

```text
analytics.betting-events.queue
```

must be bound to:

```text
betting.events
```

using exactly:

```text
bet.created
bet.updated
bet.settled
```

Messages published to unrelated routing keys must not be routed to the Analytics queue by the topology created in this task.

### Undocumented RabbitMQ properties

If `docs/events.md` does not explicitly define a topology property such as:

- durability;
- exclusivity;
- auto-delete;
- exchange arguments;
- queue arguments;

tests must not invent an exact contractual value solely to constrain the implementation.

The implementation should use stable RabbitMQ/Spring configuration appropriate for a restartable local environment.

If an undocumented topology property becomes necessary for correctness, stop and report the contractual gap instead of silently creating a new public contract.

## Messaging constants

The initial messaging names must have a single stable representation in code and must not be duplicated as unrelated string literals across services.

The contract must expose equivalent constants for:

```text
Exchange:
betting.events

Queue:
analytics.betting-events.queue

Routing keys:
bet.created
bet.updated
bet.settled

Event types:
BET_CREATED
BET_UPDATED
BET_SETTLED

Producer:
betting-service

Version:
1
```

Exact Java class or constant names are implementation details unless already established by the project architecture.

Do not introduce a dependency from one concrete microservice implementation into another merely to reuse constants.

If shared code is required, it must remain a neutral messaging contract dependency rather than coupling Analytics to Betting implementation classes or Betting to Analytics implementation classes.

## Event envelope

All version-one events must use the same logical envelope:

```json
{
  "eventId": "3df04e41-6a77-4c8e-9c6f-b663d68c1c92",
  "eventType": "BET_CREATED",
  "occurredAt": "2026-07-21T21:00:00Z",
  "version": 1,
  "producer": "betting-service",
  "payload": {}
}
```

The envelope fields are:

```text
eventId
eventType
occurredAt
version
producer
payload
```

No initial event may use a different top-level envelope.

### eventId

Type:

```text
UUID
```

Required:

```text
yes
```

`eventId` identifies the event instance, not the Bet.

Different events must be able to use different `eventId` values even when they reference the same Bet.

Generation of lifecycle event IDs during real publishing belongs to Task 6.2.

### eventType

Serialized JSON type:

```text
string
```

Required:

```text
yes
```

Allowed version-one values:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

Java may represent the field using an enum or equivalent strongly typed structure, provided JSON serialization produces exactly the documented string values.

Unknown event types are not valid version-one envelopes.

Consumer acknowledgement/retry behavior for unknown event types belongs to Task 6.3.

### occurredAt

Type:

```text
Instant
```

Serialized format:

```text
ISO-8601
```

Required:

```text
yes
```

Example:

```text
2026-07-21T21:00:00Z
```

Task 6.1 validates representation only.

The exact lifecycle instant used when real events are produced belongs to Task 6.2.

### version

Type:

```text
integer
```

Required:

```text
yes
```

Version-one events must use:

```text
1
```

Task 6.1 must not create version `2` or introduce version negotiation.

### producer

Type:

```text
string
```

Required:

```text
yes
```

Initial value:

```text
betting-service
```

The serialized version-one betting event must not allow an arbitrary producer value to silently masquerade as a valid Betting Service event contract.

### payload

Serialized JSON type:

```text
object
```

Required:

```text
yes
```

The payload must be explicit and event-specific.

A valid version-one envelope must not contain a missing or `null` payload.

The payload must not be represented as an opaque JSON string.

## Event payload contracts

Task 6.1 defines the serializable payload structures required by the initial event contract.

Task 6.1 does not populate these payloads from real application operations. Mapping real persisted Bets into these payloads belongs to Task 6.2.

### BET_CREATED payload

Routing key:

```text
bet.created
```

Event type:

```text
BET_CREATED
```

Payload fields:

```text
betId
userId
sport
league
homeTeam
awayTeam
market
selection
odds
stake
status
placedAt
```

Logical JSON shape:

```json
{
  "betId": "f8c6eb32-54d4-4024-9581-7a0a8d6f4f19",
  "userId": "b40da580-a017-4a11-bd42-c67aa6409166",
  "sport": "FOOTBALL",
  "league": "Brasileirão Série A",
  "homeTeam": "Fortaleza",
  "awayTeam": "Bahia",
  "market": "MATCH_RESULT",
  "selection": "Fortaleza",
  "odds": 2.10,
  "stake": 100.00,
  "status": "PENDING",
  "placedAt": "2026-07-21T20:30:00Z"
}
```

Required Java value types must preserve the documented semantics:

```text
betId     -> UUID
userId    -> UUID
odds      -> BigDecimal
stake     -> BigDecimal
placedAt  -> Instant
```

The remaining textual fields may use the existing domain-compatible types or strings already established by the project.

Task 6.1 must not independently recalculate or validate Bet creation lifecycle behavior.

### BET_UPDATED payload

Routing key:

```text
bet.updated
```

Event type:

```text
BET_UPDATED
```

Payload fields:

```text
betId
userId
sport
league
homeTeam
awayTeam
market
selection
odds
stake
status
placedAt
updatedAt
```

Logical JSON shape:

```json
{
  "betId": "f8c6eb32-54d4-4024-9581-7a0a8d6f4f19",
  "userId": "b40da580-a017-4a11-bd42-c67aa6409166",
  "sport": "FOOTBALL",
  "league": "Brasileirão Série A",
  "homeTeam": "Fortaleza",
  "awayTeam": "Bahia",
  "market": "MATCH_RESULT",
  "selection": "Fortaleza",
  "odds": 2.25,
  "stake": 120.00,
  "status": "PENDING",
  "placedAt": "2026-07-21T20:30:00Z",
  "updatedAt": "2026-07-21T21:20:00Z"
}
```

Required Java value types must preserve:

```text
betId      -> UUID
userId     -> UUID
odds       -> BigDecimal
stake      -> BigDecimal
placedAt   -> Instant
updatedAt  -> Instant
```

Task 6.1 must not independently implement update lifecycle rules.

### BET_SETTLED payload

Routing key:

```text
bet.settled
```

Event type:

```text
BET_SETTLED
```

Payload fields:

```text
betId
userId
status
odds
stake
profit
returnAmount
settledAt
```

Logical JSON shape:

```json
{
  "betId": "f8c6eb32-54d4-4024-9581-7a0a8d6f4f19",
  "userId": "b40da580-a017-4a11-bd42-c67aa6409166",
  "status": "WON",
  "odds": 2.10,
  "stake": 100.00,
  "profit": 110.00,
  "returnAmount": 210.00,
  "settledAt": "2026-07-21T22:00:00Z"
}
```

Required Java value types must preserve:

```text
betId         -> UUID
userId        -> UUID
odds          -> BigDecimal
stake         -> BigDecimal
profit        -> BigDecimal
returnAmount  -> BigDecimal
settledAt     -> Instant
```

Valid settlement statuses are the lifecycle states already established by the Bet domain:

```text
WON
LOST
VOID
CASHOUT
CANCELLED
```

Task 6.1 must not calculate `profit` or `returnAmount`.

The Bet domain established in Phase 5 remains responsible for all financial calculations.

Task 6.1 only guarantees that already-calculated decimal values can be represented by and serialized through the event contract without precision loss.

## Financial contract boundary

Event serialization must never create an independent financial calculation model.

The authoritative Bet-level calculation behavior remains the domain behavior established by Phase 5.

In particular, Task 6.1 must not derive:

```text
profit
returnAmount
```

from other payload fields.

It must serialize the `BigDecimal` values supplied to the payload.

Any older explanatory example in `docs/events.md` that could be interpreted as moving settlement calculation into messaging must not be implemented as messaging business logic.

Task 6.2 must later publish the values already produced by the approved Bet domain.

## Serialization

Events must serialize as JSON.

Date/time values must serialize as ISO-8601 instants.

Examples:

```text
2026-07-21T21:00:00Z
2026-07-21T22:00:00Z
```

Financial and odds values must serialize as JSON numbers, not strings.

Java representations for:

```text
odds
stake
profit
returnAmount
```

must use:

```text
BigDecimal
```

Do not use:

```text
double
float
Double
Float
```

for event financial values or odds.

Serialization/deserialization must not introduce binary floating-point conversion.

Tests must compare decimal numeric values rather than requiring a particular number of trailing zero characters in the serialized JSON unless a textual representation is explicitly documented.

For example, the following may represent the same numeric contract:

```text
2.1
2.10
2.1000
```

provided the deserialized value remains the expected `BigDecimal` value and no precision required by the domain is lost.

## Version-one validation

A version-one envelope constructed by project code must reject invalid required envelope data.

At minimum, the contract must not allow a valid envelope instance with:

```text
eventId = null
eventType = null
occurredAt = null
payload = null
```

Version must be:

```text
1
```

Producer must represent:

```text
betting-service
```

Event type must be one of:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

Validation may be implemented using constructors, factories, records with validation, or another architecture-compatible mechanism.

Do not introduce Spring dependencies into otherwise pure messaging contract types solely for validation.

### Deserialization failures

Malformed JSON or JSON that cannot be converted to the version-one contract may fail deserialization safely.

Task 6.1 does not define:

- message acknowledgement;
- retry behavior;
- dead-letter behavior;
- logging policy for consumer failures.

Those are consumer concerns owned by Task 6.3 or later infrastructure tasks.

Tests in Task 6.1 must not invent exact RabbitMQ retry/DLQ behavior for malformed messages.

## Security and privacy

The event contract must contain only the documented business data.

Envelope or payloads must not expose:

```text
password
password hash
email
JWT
Authorization header
refresh token
X-User-Id header
database credentials
payment data
internal persistence entities
stack traces
```

Users are identified in betting events only by:

```text
userId
```

Event DTOs/contracts must not depend directly on JPA entities.

## Architecture

Messaging contract code must remain independent of:

- controllers;
- JPA entities;
- repositories;
- application use-case implementations.

Concrete RabbitMQ/Spring AMQP configuration belongs to infrastructure.

Business/domain code must not depend on RabbitMQ-specific APIs.

Do not introduce imports such as RabbitTemplate, Queue, Exchange, Binding, or Spring AMQP types into the Bet domain.

Task 6.1 may configure infrastructure beans required to declare the documented topology.

Task 6.1 must not modify:

- `CreateBetService`;
- `UpdateBetService`;
- `SettleBetService`;

to publish lifecycle events.

Those integrations belong to Task 6.2.

Analytics application/domain code must not depend on Betting implementation classes.

If contract types are shared, they must live behind a neutral messaging contract boundary consistent with the existing multi-project architecture.

## RabbitMQ connection configuration

Use the RabbitMQ infrastructure already established in Phase 1.

Application connection settings must remain configuration/environment driven.

Do not hardcode production credentials.

Task 6.1 must not introduce a second RabbitMQ broker configuration or another local broker.

Local tests may use isolated RabbitMQ containers according to the project's testing strategy.

## Acceptance criteria

- [ ] Exchange `betting.events` is declared as a topic exchange.
- [ ] Queue `analytics.betting-events.queue` is declared.
- [ ] Queue is bound to `betting.events` using `bet.created`.
- [ ] Queue is bound to `betting.events` using `bet.updated`.
- [ ] Queue is bound to `betting.events` using `bet.settled`.
- [ ] The initial topology introduces no undocumented lifecycle routing keys.
- [ ] Messages using each documented routing key can be routed to the documented queue in an integration environment.
- [ ] An unrelated routing key is not routed by these bindings.
- [ ] Messaging constants preserve the exact documented exchange, queue, routing keys, event types, producer, and version.
- [ ] A common version-one envelope includes `eventId`, `eventType`, `occurredAt`, `version`, `producer`, and `payload`.
- [ ] `eventId` is represented as UUID.
- [ ] `occurredAt` is represented as an Instant and serialized as ISO-8601.
- [ ] Version-one envelope uses version `1`.
- [ ] Version-one betting envelope uses producer `betting-service`.
- [ ] Initial event types are limited to `BET_CREATED`, `BET_UPDATED`, and `BET_SETTLED`.
- [ ] `BetCreated` payload matches the documented version-one fields.
- [ ] `BetUpdated` payload matches the documented version-one fields.
- [ ] `BetSettled` payload matches the documented version-one fields.
- [ ] Odds and monetary payload fields use `BigDecimal`.
- [ ] JSON serialization preserves decimal values without `double`/`float` conversion.
- [ ] JSON round-trip preserves UUIDs, event type, timestamps, version, producer, payload fields, and decimal values.
- [ ] Missing required envelope values cannot produce a valid version-one envelope.
- [ ] Invalid version or producer is rejected by the version-one contract.
- [ ] Messaging contract types do not depend on JPA entities.
- [ ] Bet domain remains independent of RabbitMQ/Spring AMQP.
- [ ] No lifecycle application service publishes an event in Task 6.1.

## Boundary and negative cases

Tests must cover, where applicable:

### Topology

- [ ] All three documented routing keys are bound.
- [ ] An unrelated routing key does not match the Analytics queue bindings.
- [ ] No future routing key is accidentally required by the initial topology.

### Envelope

- [ ] `eventId = null`.
- [ ] `eventType = null`.
- [ ] unknown event type.
- [ ] `occurredAt = null`.
- [ ] `payload = null`.
- [ ] version different from `1`.
- [ ] invalid producer for the version-one Betting contract.

### Serialization

- [ ] UUID round-trip.
- [ ] Instant round-trip.
- [ ] `BigDecimal` odds round-trip.
- [ ] `BigDecimal` stake round-trip.
- [ ] `BigDecimal` profit round-trip.
- [ ] `BigDecimal` return amount round-trip.
- [ ] decimal value requiring scale/precision is not converted through binary floating point.
- [ ] malformed JSON fails safely at the serialization boundary.

Do not require consumer retry, acknowledgement, DLQ, or projection behavior in these tests.

## Expected tests

### Contract/unit tests

Pure tests should validate:

- messaging constants;
- envelope validation;
- event type/version/producer rules;
- payload field serialization;
- JSON round-trip;
- UUID handling;
- ISO-8601 `Instant` handling;
- `BigDecimal` handling;
- absence of `double`/`float` financial representations.

These tests should not require RabbitMQ.

### RabbitMQ topology integration tests

Use the project's approved integration testing strategy with a real RabbitMQ instance, preferably isolated through Testcontainers where compatible with the existing test infrastructure.

Integration tests should prove:

- exchange declaration;
- queue declaration;
- the three documented bindings;
- routing through `bet.created`;
- routing through `bet.updated`;
- routing through `bet.settled`;
- unrelated routing key does not reach the documented queue.

Do not require Betting lifecycle use cases to publish these messages.

Tests may publish synthetic contract messages directly to the exchange solely to validate Task 6.1 topology.

### Regression

Existing tests from previous phases must remain Green.

Task 6.1 tests must not require implementation of Task 6.2 or Task 6.3.

## Out of scope

Task 6.1 must not implement:

- publishing after `POST /bets`;
- publishing after `PUT /bets/{id}`;
- publishing after `PATCH /bets/{id}/settle`;
- changes to `CreateBetService`;
- changes to `UpdateBetService`;
- changes to `SettleBetService`;
- Analytics event consumption;
- Analytics projection writes;
- processed event persistence;
- idempotency processing;
- retry policy;
- dead-letter exchange;
- dead-letter queue;
- transactional outbox;
- event replay;
- notification consumers;
- recommendation consumers;
- dashboard endpoints;
- Phase 6.2 behavior;
- Phase 6.3 behavior.

## Dependencies

- Phase 1 RabbitMQ infrastructure.
- Phase 2 service skeletons.
- `docs/events.md`.
- Existing project serialization/testing infrastructure.

Task 6.1 does not depend on Task 5.2 or Task 5.3 application integration because it must not publish lifecycle events yet.

Task 6.2 depends on the messaging contract created here.

## Definition of Done

Apply `docs/definition-of-done.md`.

Additionally:

- all new contract tests pass;
- all RabbitMQ topology integration tests pass;
- relevant existing suites remain Green;
- no production lifecycle event publishing exists yet;
- no Analytics consumer exists yet;
- `git diff --check` passes;
- no credentials or environment-specific secrets are committed.

## Status and evidence

Current status:

```text
QA IN REVIEW
```

Use the status/evidence table from `docs/tasks/TEMPLATE.md`.

The expected workflow is:

```text
PLANNED
  ↓
TESTS IN REVIEW
  ↓
IMPLEMENTATION IN REVIEW
  ↓
QA
  ↓
DONE
```

Human approval is required before moving from approved Red tests to implementation.

### Test-agent evidence

- Contract tests: 14 executed, 14 RED because the Task 6.1 production contract types do not yet exist.
- RabbitMQ topology tests: 5 executed, 5 RED because the documented exchange and queue topology do not yet exist.
- Architecture boundary tests: 4 executed, 4 Green.
- Test compilation: Green for `:libs:messaging-contract` and `:services:analytics-service`.
- `git diff --check`: Green.
- Human approval: approved in the task conversation on 2026-08-18.
- Historical regression note: Gateway, Auth, and historical Analytics tests were Green; Betting Service compilation remains blocked by the pre-existing missing `BetRepository` interface.

### Approved-test changes

- Human-approved correction: renamed the temporary lifecycle-publishing guard to
  `should_keep_betting_application_services_independent_of_rabbitmq_implementation` and limited it to concrete RabbitMQ/Spring AMQP dependencies. Neutral publisher ports and event types remain allowed for Task 6.2.

### Implementation-agent evidence

- Human implementation diff approval: approved in the task conversation on 2026-08-18.
- New task files exposed for final QA using only `git add -N`; no regular staging command was used.
- `./gradlew :libs:messaging-contract:compileTestJava --rerun-tasks`: Green.
- `./gradlew :libs:messaging-contract:test --rerun-tasks`: Green.
- `./gradlew :services:analytics-service:test --tests '*Task61ArchitectureBoundaryTest' --rerun-tasks`: Green.
- `./gradlew :services:analytics-service:test --tests '*Task61RabbitTopologyIntegrationTest' --rerun-tasks`: Green, 5 tests against Testcontainers RabbitMQ.
- `./gradlew :services:analytics-service:test --rerun-tasks`: Green, 13 tests.
- `./gradlew :services:analytics-service:check --rerun-tasks`: Green.
- `./gradlew :services:betting-service:check --rerun-tasks`: blocked during `compileJava` by the pre-existing missing `com.suaposta.betting.application.port.out.BetRepository` recorded by the test agent; no Betting file was changed.
- `./gradlew check --rerun-tasks` run 1: same pre-existing Betting compilation blocker.
- `./gradlew check --rerun-tasks` run 2: same pre-existing Betting compilation blocker.
