# 6.2 — Publish betting lifecycle events after persistence

## Context

Task 6.1 establishes the version-one messaging contract and RabbitMQ topology defined by `docs/events.md`.

Task 6.2 integrates that contract into the Betting Service lifecycle operations implemented in Tasks 5.2 and 5.3.

The Betting Service is the producer of:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

using:

```text
Exchange:
betting.events

Routing keys:
BET_CREATED -> bet.created
BET_UPDATED -> bet.updated
BET_SETTLED -> bet.settled
```

Lifecycle events must be published only after the corresponding Bet database change succeeds.

The initial publishing model is intentionally simple:

```text
Application operation
  ↓
persist Bet
  ↓
persistence succeeds
  ↓
build version-one event
  ↓
publish through application output port
  ↓
RabbitMQ adapter
  ↓
betting.events
```

Transactional outbox, broker retry policies, and distributed atomicity between PostgreSQL and RabbitMQ are intentionally outside this task.

## Objective

Publish exactly one documented lifecycle event after each successful Betting Service state-changing operation:

```text
POST /bets
    -> BET_CREATED
    -> bet.created

PUT /bets/{id}
    -> BET_UPDATED
    -> bet.updated

PATCH /bets/{id}/settle
    -> BET_SETTLED
    -> bet.settled
```

The published envelope, payload, event type, producer, version, and routing key must match the version-one contract established by Task 6.1 and `docs/events.md`.

Publishing must not occur when the corresponding persistence operation fails.

## Architectural boundary

Publishing must be integrated through an application output port.

The intended dependency direction remains:

```text
presentation
    ↓
application
    ↓
domain

application
    ↓
messaging output port
    ↑
RabbitMQ infrastructure adapter
```

The application layer must not depend directly on:

- `RabbitTemplate`;
- RabbitMQ client APIs;
- Spring AMQP exchange classes;
- Spring AMQP queue classes;
- Spring AMQP binding classes.

The Bet domain must remain completely unaware of messaging.

The domain must not:

- publish events;
- construct RabbitMQ messages;
- know routing keys;
- depend on Spring AMQP;
- depend on the RabbitMQ client.

Concrete RabbitMQ publishing belongs in infrastructure.

## Publisher port

Introduce or reuse a single application output port representing lifecycle event publication.

A design equivalent to:

```text
BetEventPublisher
```

is recommended.

The exact Java API may be:

```text
publish(envelope, routingKey)
```

or explicit lifecycle methods such as:

```text
publishCreated(...)
publishUpdated(...)
publishSettled(...)
```

provided that:

- the application layer depends only on the port;
- infrastructure implements the port;
- RabbitMQ-specific types do not leak through the port;
- the version-one event contract from Task 6.1 is reused;
- no duplicate publisher abstraction is created unnecessarily.

Blind tests may establish a minimal concrete publisher seam compatible with these requirements.

Do not create one unrelated publisher abstraction per application service unless there is a demonstrated architectural need.

## Persistence-before-publish rule

The central invariant of Task 6.2 is:

```text
persist first
publish second
```

For every lifecycle operation:

1. validate the operation;
2. apply domain behavior;
3. persist the resulting Bet;
4. only after persistence succeeds, construct/publish the matching event.

Publishing before persistence is forbidden.

An event must never advertise a Bet state that failed to persist.

### Persistence failure

If:

```text
BetRepository.save(...)
```

or the equivalent persistence operation fails:

- the lifecycle event must not be published;
- the publisher port must not be invoked;
- no alternative event should be emitted.

This applies to:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

### Domain/application failure before persistence

If an operation fails before persistence because of:

- invalid Stake;
- invalid Odds;
- invalid lifecycle transition;
- missing Bet;
- cross-user access;
- malformed operation data;
- missing CASHOUT return amount;
- another approved domain/application validation;

no lifecycle event may be published.

## Database and RabbitMQ atomicity boundary

Task 6.2 does not implement distributed atomicity between PostgreSQL and RabbitMQ.

The first version explicitly accepts the limitation of:

```text
database persistence
then
direct RabbitMQ publication
```

This means publication failure after successful persistence cannot roll back an already successful database operation through a distributed transaction.

Do not implement:

- transactional outbox;
- XA/distributed transactions;
- event table;
- polling publisher;
- broker retry subsystem;
- compensating database rollback.

Tests must not invent atomic database-plus-RabbitMQ guarantees that are not provided by this task.

A publication failure must not be silently converted into a successful publication.

The failure must remain observable to the application/infrastructure boundary according to the project's existing error handling/logging strategy.

Do not claim an event was delivered if RabbitMQ publishing failed.

The exact HTTP response behavior for a broker failure does not become a new public REST contract in this task unless explicitly documented elsewhere.

## Event identity

Each successfully published lifecycle event must have its own:

```text
eventId
```

represented as:

```text
UUID
```

`eventId` identifies the event occurrence, not the Bet.

Different lifecycle events for the same Bet must receive different event IDs.

Example:

```text
Bet A created
eventId = UUID-1

Bet A updated
eventId = UUID-2

Bet A settled
eventId = UUID-3
```

Do not reuse:

```text
betId
```

as:

```text
eventId
```

The generation mechanism must remain testable.

Using:

```text
UUID.randomUUID()
```

inside an isolated event factory is acceptable if the current testing strategy can assert event identity without prescribing exact UUID values.

A dedicated injectable ID generator may also be used if consistent with the existing architecture.

Do not introduce unnecessary infrastructure solely to generate UUIDs.

## Envelope metadata

Every published event must use the version-one envelope established in Task 6.1.

Required top-level fields:

```text
eventId
eventType
occurredAt
version
producer
payload
```

Version must be:

```text
1
```

Producer must be:

```text
betting-service
```

Allowed event types are:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

No additional lifecycle event type belongs to Task 6.2.

## occurredAt

`occurredAt` represents the persisted lifecycle operation that caused the event.

To avoid creating an independent messaging timestamp that disagrees with the persisted Bet state, Task 6.2 should derive `occurredAt` from the server-controlled persisted lifecycle timestamp whenever that timestamp exists.

The expected mapping is:

```text
BET_CREATED:
occurredAt = persisted Bet createdAt

BET_UPDATED:
occurredAt = persisted Bet updatedAt

BET_SETTLED:
occurredAt = persisted Bet updatedAt
```

For the current settlement model, successful settlement sets:

```text
settledAt
updatedAt
```

from the same application-controlled operation instant.

Therefore a normal Task 6.2 settlement event should have:

```text
occurredAt == persisted updatedAt
```

and the payload should contain:

```text
settledAt == persisted settledAt
```

Do not call `Instant.now()` separately during event mapping when the persisted Bet already contains the authoritative lifecycle timestamp.

Messaging must describe the state that was successfully persisted, not create a second competing timeline.

## Event construction source

Events must be built from the successfully persisted Bet returned by the repository operation.

Prefer:

```text
persisted = repository.save(...)
event = map(persisted)
publisher.publish(event)
```

over:

```text
event = map(request)
repository.save(...)
publisher.publish(event)
```

The request body is not the source of truth for published lifecycle events.

Published events must reflect:

- normalized Stake;
- normalized Odds;
- server-controlled ownership;
- server-controlled status;
- domain-calculated profit;
- domain-calculated return amount;
- server-controlled timestamps.

Client-controlled fields must never override the persisted values included in an event.

## BET_CREATED

### Trigger

Publish after a Bet is successfully created and persisted by the creation use case from Task 5.2.

Event type:

```text
BET_CREATED
```

Routing key:

```text
bet.created
```

Exchange:

```text
betting.events
```

### Payload

The payload must use the Task 6.1 version-one `BET_CREATED` contract:

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

All values must come from the persisted Bet.

In particular:

```text
betId
userId
status
odds
stake
```

must not come directly from client-controlled input.

### Rules

A successful creation publishes exactly one:

```text
BET_CREATED
```

Creation must not publish:

```text
BET_UPDATED
BET_SETTLED
```

Failed creation publishes no lifecycle event.

The event must be published only after persistence succeeds.

## BET_UPDATED

### Trigger

Publish after an owned `PENDING` Bet is successfully updated and persisted by the update use case from Task 5.3.

Event type:

```text
BET_UPDATED
```

Routing key:

```text
bet.updated
```

Exchange:

```text
betting.events
```

### Payload

The payload must use the Task 6.1 version-one `BET_UPDATED` contract:

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

All payload values must come from the successfully persisted updated Bet.

Published `odds` and `stake` therefore use the values already normalized by the approved Phase 5 domain.

### Rules

A successful regular update publishes exactly one:

```text
BET_UPDATED
```

It must not publish:

```text
BET_CREATED
BET_SETTLED
```

A failed update publishes no lifecycle event.

Attempting to update a final Bet must publish no event.

A settlement must never be represented as:

```text
BET_UPDATED
```

Settlement uses only:

```text
BET_SETTLED
```

## BET_SETTLED

### Trigger

Publish after an owned `PENDING` Bet is successfully settled and the final state is persisted by the settlement use case from Task 5.3.

Event type:

```text
BET_SETTLED
```

Routing key:

```text
bet.settled
```

Exchange:

```text
betting.events
```

Valid settlement statuses are:

```text
WON
LOST
VOID
CASHOUT
CANCELLED
```

All of them publish:

```text
BET_SETTLED
```

There is no separate version-one event for:

```text
BET_CANCELLED
```

and no routing key:

```text
bet.cancelled
```

in Task 6.2.

### Payload

The payload must use the Task 6.1 version-one `BET_SETTLED` contract:

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

The event must use the final persisted Bet values.

Task 6.2 must not calculate:

```text
profit
returnAmount
```

The authoritative calculation remains the Bet domain implemented and approved in Phase 5.

The publisher must only transport those calculated values.

### Rules

A successful settlement publishes exactly one:

```text
BET_SETTLED
```

It must not publish:

```text
BET_CREATED
BET_UPDATED
```

Failed settlement publishes no event.

Examples that publish no event:

```text
PENDING -> PENDING
WON -> WON
WON -> LOST
LOST -> WON
CASHOUT -> CASHOUT
CANCELLED -> WON
CASHOUT without returnAmount
missing Bet
cross-user Bet
```

## Financial values

Task 6.2 must preserve the approved Phase 5 financial results.

Messaging must not recalculate settlement.

All event values representing:

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

Published values must be taken from the persisted Bet after domain normalization.

Examples:

```text
Stake input:
120.126

Persisted:
120.13

Published:
120.13
```

```text
Odds input:
2.12555

Persisted:
2.1256

Published:
2.1256
```

```text
CASHOUT input:
80.126

Persisted returnAmount:
80.13

Persisted profit:
-19.87

Published returnAmount:
80.13

Published profit:
-19.87
```

JSON textual trailing zero formatting is not part of the contract.

Numeric value preservation is required.

## CreateBetService integration

Evolve the existing creation application service to publish after successful persistence.

Conceptually:

```text
validate/create Bet
↓
repository.save
↓
build BET_CREATED from persisted Bet
↓
publisher port
↓
return persisted Bet
```

The service must not publish before:

```text
repository.save
```

If persistence throws:

```text
publisher interactions = 0
```

Do not move creation business rules into messaging code.

## UpdateBetService integration

Evolve the existing update application service to publish after successful persistence.

Conceptually:

```text
ownership-scoped lookup
↓
domain update
↓
repository.save
↓
build BET_UPDATED from persisted Bet
↓
publisher port
↓
return persisted Bet
```

Preserve the Task 5.3 constructor seam using `Clock`.

Adding a publisher dependency is expected.

Do not remove deterministic time behavior.

Do not change update lifecycle rules.

If lookup, validation, domain update, or persistence fails:

```text
publisher interactions = 0
```

## SettleBetService integration

Evolve the existing settlement application service to publish after successful persistence.

Conceptually:

```text
ownership-scoped lookup
↓
domain settlement
↓
repository.save
↓
build BET_SETTLED from persisted Bet
↓
publisher port
↓
return persisted Bet
```

Preserve the Task 5.3 `Clock` seam.

Do not recalculate settlement inside the event publisher or application mapper.

If lookup, lifecycle validation, financial validation, or persistence fails:

```text
publisher interactions = 0
```

## Ownership and identity

Task 6.2 must not alter the authenticated ownership model established in Phase 5.

The Betting Service continues to trust only the internal authenticated identity propagated as:

```text
X-User-Id
```

Lifecycle events must use:

```text
userId = persisted Bet userId
```

The publisher must not obtain ownership from:

- request body;
- JWT claims parsed by the Betting Service;
- Authorization header;
- event input supplied by the client.

Task 6.2 must not introduce JWT validation into the Betting Service.

## Event mapping

A dedicated application-compatible event mapper/factory may be introduced.

It must map:

```text
persisted Bet
    ↓
documented payload
    ↓
version-one envelope
```

The mapper/factory must not depend on:

- controllers;
- HTTP request DTOs;
- JPA entities;
- `RabbitTemplate`.

Prefer one coherent event construction component over duplicated mapping code across the three application services.

Exact class names are implementation details unless established by Task 6.1.

The event factory may own:

- event type selection;
- payload construction;
- producer/version metadata;
- event ID generation where appropriate.

It must not own Bet financial calculations.

## RabbitMQ adapter

Infrastructure must implement the application publisher port using the Task 6.1 RabbitMQ configuration.

The adapter must publish to:

```text
betting.events
```

using the routing key associated with the envelope/event being published.

Mappings are exactly:

```text
BET_CREATED
-> bet.created

BET_UPDATED
-> bet.updated

BET_SETTLED
-> bet.settled
```

Do not infer routing keys dynamically from arbitrary strings if doing so can produce undocumented keys.

The routing relationship should use the stable Task 6.1 messaging contract.

## Serialization

Reuse the JSON serialization configuration and version-one contract created in Task 6.1.

Do not create a second incompatible event serializer.

RabbitMQ messages must preserve:

- envelope fields;
- event-specific payload fields;
- UUID values;
- ISO-8601 timestamps;
- `BigDecimal` values.

The serialized event must not contain JPA entity internals.

## Serialization failure

If constructing or serializing a lifecycle event fails after persistence:

- do not publish malformed data;
- do not substitute an incomplete event;
- do not publish a different event;
- do not silently claim success at the publisher boundary.

The persisted Bet may already exist because distributed atomicity is outside scope.

Do not attempt to manually reverse the persisted lifecycle operation as a substitute for a transactional outbox.

Tests must explicitly distinguish:

```text
persistence failure
```

from:

```text
publication/serialization failure after successful persistence
```

## Publication failure

If the RabbitMQ adapter cannot publish after persistence succeeds:

- the persisted database state remains authoritative;
- the publisher failure must remain observable;
- the application must not publish a second lifecycle type as fallback;
- Task 6.2 must not implement retry loops;
- Task 6.2 must not implement an outbox;
- Task 6.2 must not compensate by reverting the Bet.

The architecture must make this known first-version limitation explicit rather than hiding it.

## Security and privacy

Published lifecycle events must contain only fields documented by Task 6.1 and `docs/events.md`.

Messages must not contain:

```text
password
password hash
email
JWT
Authorization header
refresh token
X-User-Id header
database credentials
HTTP request objects
JPA entities
stack traces
internal exception details
```

The user is represented only by:

```text
userId
```

Do not serialize whole controller requests, authenticated principal objects, or persistence entities into RabbitMQ.

## Exactly-once scope

Task 6.2 guarantees only application-level intent:

```text
one successful lifecycle operation
-> one publisher invocation
```

Task 6.2 does not guarantee distributed exactly-once delivery.

RabbitMQ redelivery and Analytics idempotency belong to Task 6.3.

Tests must not require an exactly-once guarantee across process crashes or broker retries.

## Acceptance criteria

- [ ] Successful Bet creation persists the Bet before lifecycle publication.
- [ ] Successful Bet creation publishes exactly one `BET_CREATED`.
- [ ] `BET_CREATED` uses exchange `betting.events`.
- [ ] `BET_CREATED` uses routing key `bet.created`.
- [ ] `BET_CREATED` payload matches the Task 6.1 contract.
- [ ] `BET_CREATED` payload comes from the successfully persisted Bet.
- [ ] Failed creation publishes no lifecycle event.
- [ ] Persistence failure during creation publishes no lifecycle event.
- [ ] Successful Bet update persists the Bet before lifecycle publication.
- [ ] Successful Bet update publishes exactly one `BET_UPDATED`.
- [ ] `BET_UPDATED` uses exchange `betting.events`.
- [ ] `BET_UPDATED` uses routing key `bet.updated`.
- [ ] `BET_UPDATED` payload matches the Task 6.1 contract.
- [ ] `BET_UPDATED` payload comes from the successfully persisted updated Bet.
- [ ] Failed update publishes no lifecycle event.
- [ ] Persistence failure during update publishes no lifecycle event.
- [ ] Successful settlement persists the Bet before lifecycle publication.
- [ ] Every successful final settlement publishes exactly one `BET_SETTLED`.
- [ ] `BET_SETTLED` uses exchange `betting.events`.
- [ ] `BET_SETTLED` uses routing key `bet.settled`.
- [ ] `BET_SETTLED` payload matches the Task 6.1 contract.
- [ ] `BET_SETTLED` payload comes from the successfully persisted settled Bet.
- [ ] `WON` publishes `BET_SETTLED`.
- [ ] `LOST` publishes `BET_SETTLED`.
- [ ] `VOID` publishes `BET_SETTLED`.
- [ ] `CASHOUT` publishes `BET_SETTLED`.
- [ ] `CANCELLED` publishes `BET_SETTLED`.
- [ ] Invalid/repeated settlement publishes no event.
- [ ] Persistence failure during settlement publishes no lifecycle event.
- [ ] Event envelope uses a unique UUID `eventId`.
- [ ] Event envelope uses version `1`.
- [ ] Event envelope uses producer `betting-service`.
- [ ] Event `occurredAt` represents the successfully persisted lifecycle operation.
- [ ] Published decimal values come from the persisted domain state.
- [ ] Messaging contains no independent financial calculation.
- [ ] Application services depend on a publisher port, not RabbitMQ APIs.
- [ ] RabbitMQ adapter remains infrastructure.
- [ ] Bet domain remains independent of messaging.
- [ ] No Analytics projection logic is implemented.
- [ ] No transactional outbox is implemented.
- [ ] Existing Phase 5 behavior remains Green.

## Boundary and negative cases

Tests must cover, where applicable:

### Creation

- [ ] valid creation persists before publish;
- [ ] one successful creation produces one publisher invocation;
- [ ] invalid Stake produces no publication;
- [ ] invalid Odds produces no publication;
- [ ] persistence failure produces no publication;
- [ ] published payload uses persisted normalized Stake/Odds;
- [ ] published `userId` comes from persisted ownership.

### Update

- [ ] valid owned PENDING update persists before publish;
- [ ] one successful update produces one publisher invocation;
- [ ] invalid Stake produces no publication;
- [ ] invalid Odds produces no publication;
- [ ] final Bet update attempt produces no publication;
- [ ] missing Bet produces no publication;
- [ ] cross-user Bet produces no publication;
- [ ] persistence failure produces no publication;
- [ ] payload contains the persisted updated values;
- [ ] `updatedAt` in payload matches persisted `updatedAt`.

### Settlement

- [ ] `WON`;
- [ ] `LOST`;
- [ ] `VOID`;
- [ ] `CASHOUT`;
- [ ] `CANCELLED`;
- [ ] `PENDING -> PENDING` produces no publication;
- [ ] final -> final produces no publication;
- [ ] CASHOUT without return amount produces no publication;
- [ ] missing Bet produces no publication;
- [ ] cross-user Bet produces no publication;
- [ ] persistence failure produces no publication;
- [ ] published profit equals persisted domain-calculated profit;
- [ ] published return amount equals persisted domain-calculated return amount;
- [ ] published `settledAt` equals persisted `settledAt`.

### Event identity

- [ ] event ID is non-null UUID;
- [ ] separate lifecycle events receive separate event IDs;
- [ ] `eventId` is not copied from `betId`.

### Failure after persistence

- [ ] serialization/publication failure does not result in malformed publication;
- [ ] successful persistence is not falsely reported as rolled back by messaging code;
- [ ] no retry/outbox behavior is invented.

## Expected tests

### Application unit tests

Use mocks/fakes for:

```text
BetRepository
publisher port
```

These tests must not require RabbitMQ.

For each lifecycle operation prove ordering:

```text
repository.save
before
publisher.publish
```

Where practical, use ordered verification or another deterministic mechanism.

Application tests should cover:

```text
CreateBetService
UpdateBetService
SettleBetService
```

and prove:

- correct event type;
- correct payload;
- publisher invoked exactly once after success;
- publisher not invoked before/when persistence fails;
- publisher not invoked when domain/application behavior fails;
- persisted Bet is the event source.

Do not weaken existing Phase 5 tests.

### Event mapping/unit tests

Cover mapping from persisted Bet to:

```text
BET_CREATED payload
BET_UPDATED payload
BET_SETTLED payload
```

Validate:

- identity;
- normalized decimals;
- lifecycle status;
- timestamps;
- financial values;
- exact version-one event type;
- producer/version metadata.

Do not duplicate the full Task 6.1 serialization suite unnecessarily.

### Messaging integration tests

Use the real RabbitMQ topology established by Task 6.1.

Integration tests should demonstrate that the RabbitMQ publisher adapter sends:

```text
BET_CREATED -> bet.created
BET_UPDATED -> bet.updated
BET_SETTLED -> bet.settled
```

to:

```text
betting.events
```

and that messages are routable to:

```text
analytics.betting-events.queue
```

Verify the received envelope/payload.

Synthetic application fixtures are acceptable.

Do not require an Analytics consumer.

### Full lifecycle integration

At least one integration path should prove for each operation:

```text
application operation
↓
persistence succeeds
↓
event is published
↓
message matches persisted state
```

Do not make these tests depend on a running Analytics Service.

### Regression

All existing tests from:

- Task 5.1;
- Task 5.2;
- Task 5.3;
- Task 6.1;

must remain Green.

## Protected behavior

Task 6.2 must not weaken or alter approved Phase 5 behavior.

Preserve:

- authenticated ownership;
- missing/cross-user indistinguishability;
- Stake normalization;
- Odds normalization;
- financial calculations;
- lifecycle restrictions;
- settlement atomicity;
- server-controlled timestamps;
- pagination defaults;
- safe REST errors.

Messaging integration must adapt to the existing business behavior.

Existing business behavior must not be changed merely to simplify event publishing.

## Out of scope

Task 6.2 must not implement:

- Analytics event consumption;
- Analytics projections;
- `processed_events`;
- consumer idempotency;
- consumer retry handling;
- consumer acknowledgement strategy;
- consumer out-of-order handling;
- dead-letter exchange;
- dead-letter queue;
- publisher retry policy;
- transactional outbox;
- distributed transactions;
- XA;
- event replay;
- event correction;
- delete events;
- `bet.deleted`;
- `bet.cancelled`;
- `bet.corrected`;
- notification events;
- recommendation events;
- dashboard endpoints;
- Phase 6.3 behavior.

## Dependencies

- Task 5.2 — create and retrieve Bets.
- Task 5.3 — update and settle Bets.
- Task 6.1 — messaging contract and RabbitMQ topology.
- `docs/events.md`.
- RabbitMQ infrastructure from Phase 1.

## Definition of Done

Apply `docs/definition-of-done.md`.

Additionally:

- lifecycle publisher port exists at the application boundary;
- RabbitMQ publisher implementation exists only in infrastructure;
- successful creation publishes `BET_CREATED`;
- successful update publishes `BET_UPDATED`;
- successful settlement publishes `BET_SETTLED`;
- persistence always precedes publishing;
- failed persistence publishes nothing;
- failed domain/application operations publish nothing;
- published payloads represent the persisted Bet state;
- Phase 5 tests remain Green;
- Task 6.1 tests remain Green;
- relevant RabbitMQ integration tests pass;
- no outbox/retry/DLQ implementation is introduced;
- `git diff --check` passes;
- no credentials or environment-specific secrets are committed.

## Status and evidence

Current status:

```text
DONE
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

| Gate | Decision / evidence | Date / approver |
| --- | --- | --- |
| Specification provided | Task 6.2 specification and required architecture, event, API, testing, and Phase 5 documents reviewed. | 2026-08-18 / test agent |
| Tests in Red | `compileJava` passed; `compileTestJava` and `test --tests '*Task62*'` fail only because production `BetEventPublisher` is absent. | 2026-08-18 / test agent |
| Tests approved | Human approval explicitly provided in the Codex conversation; no approved test was changed after approval. | 2026-08-18 / human |
| Implementation in Green | Task 6.2 passed 43/43 focused tests; the complete Betting suite passed 211/211; Betting, messaging contract, Analytics, Auth, Gateway, and two root checks passed. | 2026-08-18 / implementation agent |
| Human diff review | Human explicitly approved the implementation diff and requested handoff to final QA. | 2026-08-18 / human |
| QA verdict | APPROVED. Focused Task 6.2 tests, complete Betting Service suite, and root `check` passed; independent audit found no blockers or reservations. | 2026-08-18 / final QA agent |

### Test-agent evidence

- Created `Task62ApplicationPublishingTest`, `Task62PublisherIntegrationTest`, and `Task62ArchitectureBoundaryTest`.
- Added only test-scoped dependencies in `services/betting-service/build.gradle`.
- The publisher integration test obtains `BetEventPublisher` through a minimal Spring context and does not require a concrete Rabbit adapter constructor.
- Serialization coverage uses a cyclic payload, requires a `JsonProcessingException` cause, and verifies that no message reaches the queue.
- Historical Betting Service regression passed: 168 tests, 0 failures, using a temporary exclusion of only the three new Task 6.2 test files; the temporary init script was removed.
- `:libs:messaging-contract:test` passed.
- `:services:analytics-service:check` passed.
- `git diff --check` passed.
- No production Java file, approved test, commit, push, merge, or staging command was changed or performed.

### Approved-test changes

None.
