# 6.3 — Consume events idempotently into analytics projections

## Context

Task 6.1 establishes the version-one RabbitMQ topology and messaging contract.

Task 6.2 publishes Betting lifecycle events only after successful Betting Service persistence.

Task 6.3 introduces the Analytics Service consumer responsible for transforming those events into Analytics-owned projections.

The asynchronous flow is:

```text
Betting Service
  ↓
betting.events
  ↓
analytics.betting-events.queue
  ↓
Analytics Service
  ↓
analytics_db
```

The Analytics Service consumes:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

from:

```text
analytics.betting-events.queue
```

The Analytics Service must maintain its own projection and must never query or write the Betting Service database.

The initial consumer must also be idempotent.

RabbitMQ may deliver the same message more than once, therefore:

```text
same eventId
must not
apply the projection change twice
```

Task 6.3 owns:

- Analytics event consumption;
- version-one envelope validation at the consumer boundary;
- event-specific payload handling;
- Analytics projection persistence;
- processed-event persistence;
- idempotency;
- documented first-version handling of duplicate, unknown, malformed, and out-of-order events.

Task 6.3 does not own dashboard endpoints or advanced messaging recovery infrastructure.

## Objective

Consume valid version-one Betting lifecycle events from:

```text
analytics.betting-events.queue
```

and apply each valid event exactly once at the Analytics application level.

The result must maintain:

```text
analytics_bets
```

as an Analytics-owned representation of Betting state and:

```text
processed_events
```

as the source of idempotency for consumed events.

The intended flow is:

```text
RabbitMQ message
  ↓
deserialize envelope
  ↓
validate version-one contract
  ↓
check eventId
  ↓
if duplicate:
    acknowledge/ignore
  ↓
if new:
    apply projection change
    store processed event
  ↓
commit Analytics transaction
```

Projection mutation and processed-event registration must behave atomically inside the Analytics database transaction.

## Consumer boundary

The RabbitMQ listener belongs to infrastructure.

The intended dependency direction is:

```text
RabbitMQ
   ↓
infrastructure listener
   ↓
application consumer/use case
   ↓
application persistence ports
   ↑
infrastructure persistence adapters
```

The Analytics domain/application layers must not depend directly on:

- `RabbitTemplate`;
- RabbitMQ Java client APIs;
- Spring AMQP listener container APIs;
- queue/exchange implementation classes.

RabbitMQ-specific acknowledgement and transport concerns must remain in infrastructure.

The application layer should receive the version-one messaging contract established by Task 6.1 or an equivalent neutral contract representation.

Analytics must not depend on Betting application or persistence implementation classes.

## Source of truth

The consumed event is the source of data for the Analytics projection.

Analytics must not enrich the projection by calling the Betting Service.

Analytics must never access:

```text
betting_db
```

directly.

All projection fields required by a lifecycle operation must come from the documented event payload.

Task 6.3 must not introduce synchronous service-to-service calls to compensate for incomplete event data.

## Queue

The consumer must read from exactly:

```text
analytics.betting-events.queue
```

The queue and its bindings are established by Task 6.1.

Task 6.3 must not create a competing queue for the same initial Analytics consumer.

Do not introduce separate queues for:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

in the first version.

## Supported event types

The initial consumer supports exactly:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

Corresponding routing keys are:

```text
BET_CREATED -> bet.created
BET_UPDATED -> bet.updated
BET_SETTLED -> bet.settled
```

Task 6.3 must not introduce consumer behavior for future events such as:

```text
BET_DELETED
BET_CORRECTED
BET_CANCELLED
```

`CANCELLED` remains a settlement status carried by:

```text
BET_SETTLED
```

## Version-one envelope validation

The consumer must validate the version-one envelope before applying a projection change.

Required fields:

```text
eventId
eventType
occurredAt
version
producer
payload
```

A processable version-one Betting event requires:

```text
eventId != null
eventType != null
occurredAt != null
payload != null
version == 1
producer == betting-service
```

Supported event types:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

Malformed or unsupported envelopes must never update:

```text
analytics_bets
processed_events
```

unless a later explicitly documented policy requires otherwise.

## Payload validation

Each supported event must contain the required payload shape established by Task 6.1.

A payload that cannot safely represent the documented event must not partially update the projection.

Examples include:

- missing `betId`;
- missing `userId`;
- malformed UUID;
- malformed decimal;
- missing required lifecycle value;
- unsupported settlement status;
- incompatible payload type.

Validation may occur during deserialization or at the application boundary.

Do not duplicate Betting business calculations in Analytics validation.

The consumer validates contract integrity, not the original business decision that produced the event.

## Financial values

Analytics must treat financial values contained in valid events as authoritative persisted Betting results.

Analytics must not recalculate:

```text
profit
returnAmount
```

from:

```text
stake
odds
status
```

The Betting domain remains responsible for Bet-level financial calculations.

The consumer must preserve the values supplied by valid events.

Use:

```text
BigDecimal
```

for:

```text
odds
stake
profit
returnAmount
```

Do not use:

```text
double
float
Double
Float
```

for financial or odds values.

Persistence must preserve the precision established by the event contract.

## Analytics projection

The Analytics Service must maintain an Analytics-owned projection table equivalent to:

```text
analytics_bets
```

The projection must support the fields required by the documented events and future dashboard queries.

Minimum projection fields:

```text
id
bet_id
user_id
sport
league
home_team
away_team
market
selection
odds
stake
status
profit
return_amount
placed_at
settled_at
created_at
updated_at
```

The exact internal primary-key strategy is an implementation detail.

The business identity of a projected Bet is:

```text
betId
```

Projection persistence must support lookup/update by:

```text
betId
```

and preserve:

```text
userId
```

from the event.

The Analytics Service must not change ownership independently.

## Projection persistence port

The application layer should depend on a persistence port representing the Analytics Bet projection.

A design equivalent to:

```text
AnalyticsBetProjectionRepository
```

may support behavior such as:

```text
findByBetId
save
```

or more explicit create/update methods.

Exact Java signatures are implementation details unless established by approved tests.

Do not expose JPA entities through the application boundary.

## BET_CREATED behavior

A valid:

```text
BET_CREATED
```

creates the initial Analytics projection.

The payload supplies:

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

The initial projection should represent:

```text
status = PENDING
profit = null
returnAmount = null
settledAt = null
```

when those lifecycle values are not present in the version-one creation payload.

The projection must use the event's normalized:

```text
odds
stake
```

without recalculating them.

### Timestamp mapping

For `BET_CREATED`:

```text
createdAt = envelope.occurredAt
updatedAt = envelope.occurredAt
```

unless the version-one payload contract is later explicitly expanded with authoritative persisted creation timestamps.

This mapping is an Analytics projection concern and does not alter the Betting entity.

### Existing projection

If a projection with the same:

```text
betId
```

already exists but the incoming `eventId` is new, do not silently overwrite arbitrary existing state.

In the normal first-version lifecycle, a valid new `BET_CREATED` is expected to create a projection that does not yet exist.

An unexpected second creation for the same `betId` with a different `eventId` must be treated as an invalid lifecycle/projection conflict and must not mutate the existing projection.

This is different from duplicate delivery of the same `eventId`, which is handled by idempotency and safely ignored.

## BET_UPDATED behavior

A valid:

```text
BET_UPDATED
```

updates an existing projection.

Editable projection fields are:

```text
sport
league
home_team
away_team
market
selection
odds
stake
placed_at
updated_at
```

The event must not change settlement fields through `BET_UPDATED`.

Preserve:

```text
bet_id
user_id
created_at
status
profit
return_amount
settled_at
```

except for values explicitly owned by the documented `BET_UPDATED` contract.

For the current lifecycle:

```text
status
```

remains:

```text
PENDING
```

The `updatedAt` value must come from the documented `BET_UPDATED` payload.

Analytics must not generate a replacement business timestamp using `Instant.now()`.

### Missing projection

If:

```text
BET_UPDATED
```

arrives and no projection exists for its:

```text
betId
```

the first version must:

```text
fail processing
do not create a partial projection
do not mark eventId as processed
```

The event should remain eligible for the transport-level failure handling configured by the messaging infrastructure.

Task 6.3 must not synthesize a complete projection from the partial update event.

## BET_SETTLED behavior

A valid:

```text
BET_SETTLED
```

updates settlement fields of an existing projection.

Settlement fields:

```text
status
profit
return_amount
settled_at
updated_at
```

The payload supplies:

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

The projection must use:

```text
status
profit
returnAmount
settledAt
```

exactly from the valid event contract.

Analytics must not recalculate financial values.

### updatedAt

The version-one `BET_SETTLED` payload does not contain a separate:

```text
updatedAt
```

field.

For the current contract:

```text
updatedAt = envelope.occurredAt
```

for a successfully applied `BET_SETTLED`.

`settledAt` remains the value explicitly carried by the settlement payload.

These timestamps may be equal in the normal Phase 5 lifecycle but represent different contract fields and must not be conflated in code merely by assumption.

### Valid settlement statuses

Supported settlement projection states:

```text
WON
LOST
VOID
CASHOUT
CANCELLED
```

A `BET_SETTLED` payload with:

```text
PENDING
```

is invalid and must not mutate the projection.

### Missing projection

If:

```text
BET_SETTLED
```

arrives before a corresponding projection exists, the first version must:

```text
fail processing
do not create a partial projection
do not mark eventId as processed
```

This explicitly selects the recommended first-version behavior from the event contract:

```text
BET_CREATED creates projection
BET_UPDATED updates existing projection
BET_SETTLED updates existing projection
```

Task 6.3 must not create a synthetic projection from the settlement payload because the version-one settlement event does not contain the complete Bet snapshot.

## Idempotency

Idempotency is mandatory.

The Analytics Service must persist processed event IDs.

Use a table equivalent to:

```text
processed_events
```

Minimum fields:

```text
event_id
event_type
processed_at
```

`event_id` must be unique.

The exact internal entity/table implementation may evolve, but the persisted uniqueness of:

```text
eventId
```

must protect against duplicate processing.

## Processed event persistence port

The application layer should depend on an idempotency persistence boundary equivalent to:

```text
ProcessedEventRepository
```

Required behavior must allow the application to determine whether:

```text
eventId
```

was already processed and to register a newly processed event.

The implementation must rely on a database uniqueness constraint or equivalent durable mechanism as the final concurrency-safe guard.

A process-local:

```text
Set<UUID>
```

or in-memory cache is not sufficient.

## Duplicate event behavior

If the same:

```text
eventId
```

is delivered again:

- do not apply the projection mutation again;
- do not insert another processed-event record;
- treat the delivery as already successfully handled;
- do not raise a business failure merely because the event is duplicated.

Conceptually:

```text
eventId already processed
  ↓
ignore safely
  ↓
successful consumer completion
```

Duplicate handling must work across application restarts because the idempotency state is persisted.

## Same Bet with different eventId

Idempotency is based on:

```text
eventId
```

not:

```text
betId
```

Different legitimate lifecycle events for the same Bet must still be processed.

Example:

```text
BET_CREATED
eventId = A
betId = X

BET_UPDATED
eventId = B
betId = X

BET_SETTLED
eventId = C
betId = X
```

All three are distinct events and must be processed once each.

Do not reject an event merely because another processed event references the same Bet.

## Transactional projection processing

For a new valid event, the following actions must behave atomically within the Analytics database transaction:

```text
apply projection mutation
+
store processed eventId
```

The required outcome is:

```text
both commit
or
neither commits
```

Do not create a state where:

```text
projection changed
but eventId not registered
```

because redelivery could apply the same mutation again.

Do not create a state where:

```text
eventId registered
but projection mutation rolled back
```

because redelivery would then be incorrectly ignored.

Use the existing project transaction strategy or introduce the minimum Spring transaction boundary required in the Analytics infrastructure/application integration.

This is a local Analytics database transaction.

It is not a distributed RabbitMQ/database transaction.

## Concurrent duplicates

Two concurrent deliveries with the same:

```text
eventId
```

must not produce two projection mutations.

The persistent uniqueness constraint on:

```text
processed_events.event_id
```

must remain the final concurrency-safe protection.

An application-level pre-check alone:

```text
exists(eventId)
```

is not sufficient to guarantee correctness under concurrency.

Tests should validate the behavior at an appropriate persistence/integration level without prescribing unnecessary implementation details.

## Processing order

Strict global ordering is not guaranteed.

The normal lifecycle order is:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

Task 6.3 does not implement a general event reorder buffer.

The first-version out-of-order decisions are explicit:

### BET_UPDATED before BET_CREATED

If the projection does not exist:

```text
processing fails
projection is not created
eventId is not marked processed
```

### BET_SETTLED before BET_CREATED

If the projection does not exist:

```text
processing fails
projection is not created
eventId is not marked processed
```

### BET_CREATED after an existing projection

If a different `eventId` tries to create a Bet projection that already exists:

```text
processing fails
existing projection remains unchanged
eventId is not marked processed
```

### Stale lifecycle events

Task 6.3 does not introduce a general timestamp/version ordering engine.

Normal lifecycle ordering is expected from the producer/broker flow.

Do not silently apply an event in a way that violates the current projection lifecycle.

If a valid but semantically stale event cannot be safely applied according to the documented projection state, fail processing rather than corrupting the projection.

Advanced event ordering/version conflict resolution is outside scope.

## Unknown event type

An envelope with an event type outside:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

is not processable by the version-one consumer.

The first version must:

```text
reject/fail processing
do not mutate analytics_bets
do not register eventId as processed
```

Do not silently interpret unknown events as another known lifecycle event.

Do not introduce handlers for future event types.

## Unsupported event version

An event with:

```text
version != 1
```

must not be processed by the version-one consumer.

Behavior:

```text
fail processing
do not mutate projection
do not mark eventId as processed
```

Task 6.3 does not implement multi-version dispatch.

## Invalid producer

A version-one Betting event whose envelope does not identify:

```text
producer = betting-service
```

must not be processed as a valid Betting lifecycle event.

Behavior:

```text
fail processing
do not mutate projection
do not mark eventId as processed
```

## Malformed event behavior

Malformed JSON or an invalid envelope/payload must never be silently accepted.

Behavior:

```text
fail processing
do not mutate analytics_bets
do not register processed event
```

The failure should remain visible to the RabbitMQ/Spring messaging infrastructure.

Task 6.3 must not implement a custom dead-letter subsystem.

## RabbitMQ acknowledgement and failure boundary

The consumer must return successfully for:

```text
valid newly processed event
duplicate eventId
```

The consumer must fail for:

```text
malformed event
unknown event type
unsupported version
invalid producer
invalid payload
out-of-order event requiring missing projection
projection persistence failure
processed-event persistence failure
```

The transport infrastructure may then apply the configured RabbitMQ/Spring failure behavior.

Task 6.3 must not define or implement an advanced retry policy.

Task 6.3 must not manually loop retry attempts.

Task 6.3 must not create a DLQ.

Task 6.3 must not create a DLX.

If default framework behavior risks an uncontrolled infinite hot retry loop during tests/local execution, use the minimum safe configuration already supported by the project testing/messaging strategy and report any contractual gap before introducing a new retry policy.

Do not silently discard failed messages.

## Projection failure

If applying a projection change fails:

- do not register the event as processed;
- transaction must roll back;
- failure must propagate to the consumer infrastructure.

The event must remain logically unprocessed.

## Processed-event persistence failure

If registering:

```text
eventId
```

fails:

- projection mutation must roll back;
- consumer processing must fail;
- event must not be treated as successfully processed.

## Database ownership

Analytics owns:

```text
analytics_db
```

Betting owns:

```text
betting_db
```

Task 6.3 must not:

- connect Analytics repositories to `betting_db`;
- query Betting JPA entities;
- reuse Betting repositories;
- write Betting tables;
- introduce cross-database joins.

The only data flow from Betting into Analytics in this task is:

```text
RabbitMQ events
```

## Analytics migration

Task 6.3 owns the minimum Analytics database migration required for:

```text
analytics_bets
processed_events
```

if those tables do not already exist.

Migration must support the documented projection fields and idempotency contract.

### analytics_bets

Must provide durable storage for the projection.

At minimum:

```text
bet_id
```

must have a uniqueness rule appropriate to one projection per Bet.

Use decimal database types appropriate for:

```text
odds
stake
profit
return_amount
```

Do not persist financial values using floating-point database types.

### processed_events

Must persist:

```text
event_id
event_type
processed_at
```

`event_id` must be unique.

The database uniqueness constraint is part of the idempotency guarantee.

Do not rely only on an application-side existence check.

## Projection timestamps

Projection timestamps represent event-derived Betting lifecycle information, not Analytics processing time.

Use:

```text
createdAt
updatedAt
settledAt
```

according to the event mappings defined above.

`processed_events.processed_at` is different.

`processed_at` represents when Analytics successfully processed the event.

It may be generated by Analytics using an injected:

```text
Clock
```

or equivalent deterministic time source.

Do not use `processed_at` as a substitute for business event timestamps.

## Clock

Where Analytics generates:

```text
processedAt
```

use a testable time source.

Prefer:

```text
Clock
```

with production configuration equivalent to:

```text
Clock.systemUTC()
```

Do not scatter:

```text
Instant.now()
```

through application services where deterministic behavior is required.

## Event dispatch

The application consumer may dispatch by:

```text
eventType
```

to event-specific handlers or use another cohesive design.

The implementation must preserve:

```text
one entry boundary
+
explicit behavior per supported event type
```

Avoid large infrastructure listeners containing all projection business logic.

The RabbitMQ listener should remain thin:

```text
receive
deserialize
delegate
```

Application code should own:

```text
idempotency decision
projection orchestration
transactional processing
```

Persistence adapters should own database implementation details.

## Consumer payload mapping

Do not pass raw:

```text
JsonNode
Map<String, Object>
String JSON
```

deep into projection business logic if Task 6.1 already established strongly typed event contracts.

Prefer consuming the typed version-one envelope and explicit payload classes.

Do not create a second incompatible event contract inside Analytics.

## Security and privacy

The consumer must not log or persist undocumented sensitive data.

Events must not expose or persist:

```text
password
password hash
email
JWT
Authorization
refresh token
X-User-Id header
database credentials
payment data
stack trace
internal persistence entities
```

Analytics identifies users by:

```text
userId
```

Do not reconstruct authentication data from event payloads.

## Response to duplicate delivery

Duplicate delivery is expected messaging behavior, not an application error.

Example:

```text
eventId = A
BET_SETTLED
```

First delivery:

```text
projection updated
processed_events contains A
```

Second delivery:

```text
projection unchanged
no second processed record
consumer completes successfully
```

This must be demonstrated by tests.

## Acceptance criteria

- [ ] Analytics consumes from `analytics.betting-events.queue`.
- [ ] Consumer supports `BET_CREATED`.
- [ ] Consumer supports `BET_UPDATED`.
- [ ] Consumer supports `BET_SETTLED`.
- [ ] Valid version-one envelope is validated before projection mutation.
- [ ] `BET_CREATED` creates the expected Analytics projection.
- [ ] `BET_UPDATED` updates only the documented editable projection fields.
- [ ] `BET_SETTLED` updates only the documented settlement projection fields.
- [ ] Analytics does not recalculate Betting financial results.
- [ ] Odds and monetary values use `BigDecimal`.
- [ ] Analytics never queries or writes `betting_db`.
- [ ] Processed event IDs are stored durably.
- [ ] `processed_events.event_id` is unique.
- [ ] First delivery of a valid `eventId` applies exactly one projection change.
- [ ] Repeated delivery of the same `eventId` applies no additional projection change.
- [ ] Duplicate delivery completes successfully.
- [ ] Distinct event IDs for the same Bet are processed independently.
- [ ] Projection change and processed-event registration occur in one Analytics transaction.
- [ ] Projection failure does not mark the event processed.
- [ ] Processed-event persistence failure does not leave the projection committed.
- [ ] Concurrent duplicate protection does not rely only on process-local state.
- [ ] `BET_UPDATED` for a missing projection fails without creating a partial projection.
- [ ] `BET_SETTLED` for a missing projection fails without creating a partial projection.
- [ ] Unexpected second `BET_CREATED` with a different eventId does not overwrite an existing projection.
- [ ] Unknown event type produces no projection mutation.
- [ ] Unsupported version produces no projection mutation.
- [ ] Invalid producer produces no projection mutation.
- [ ] Malformed envelope produces no projection mutation.
- [ ] Invalid payload produces no projection mutation.
- [ ] Failed events are not registered as processed.
- [ ] RabbitMQ listener remains infrastructure-only.
- [ ] Application/domain code does not depend on RabbitMQ APIs.
- [ ] Analytics persistence remains independent from Betting persistence.
- [ ] No advanced retry/DLQ implementation is introduced.
- [ ] Existing Tasks 6.1 and 6.2 behavior remains Green.

## Boundary and negative cases

Tests must cover, where applicable:

### BET_CREATED

- [ ] valid creation event;
- [ ] projection fields match payload;
- [ ] `createdAt` uses envelope `occurredAt`;
- [ ] `updatedAt` initially uses envelope `occurredAt`;
- [ ] financial settlement fields remain unset;
- [ ] same `eventId` delivered twice;
- [ ] different `eventId` attempting to create existing projection;
- [ ] invalid/missing payload field;
- [ ] persistence failure.

### BET_UPDATED

- [ ] valid update after existing creation;
- [ ] editable fields updated;
- [ ] ownership remains event `userId`;
- [ ] `createdAt` preserved;
- [ ] settlement fields preserved;
- [ ] `updatedAt` comes from payload;
- [ ] duplicate `eventId`;
- [ ] missing projection;
- [ ] invalid payload;
- [ ] projection persistence failure.

### BET_SETTLED

- [ ] `WON`;
- [ ] `LOST`;
- [ ] `VOID`;
- [ ] `CASHOUT`;
- [ ] `CANCELLED`;
- [ ] `profit` preserved exactly from event;
- [ ] `returnAmount` preserved exactly from event;
- [ ] `settledAt` comes from payload;
- [ ] `updatedAt` comes from envelope `occurredAt`;
- [ ] `createdAt` preserved;
- [ ] duplicate `eventId`;
- [ ] missing projection;
- [ ] invalid `PENDING` settlement payload;
- [ ] invalid payload;
- [ ] projection persistence failure.

### Envelope

- [ ] null/missing eventId;
- [ ] null/missing eventType;
- [ ] null/missing occurredAt;
- [ ] null/missing payload;
- [ ] unknown eventType;
- [ ] version other than `1`;
- [ ] producer other than `betting-service`;
- [ ] malformed JSON.

### Idempotency

- [ ] first event is processed;
- [ ] processed event is persisted;
- [ ] duplicate same eventId is ignored;
- [ ] duplicate does not mutate projection;
- [ ] duplicate does not create second processed-event record;
- [ ] duplicate succeeds after application restart/persistent reload;
- [ ] different eventIds for same betId remain independent;
- [ ] unique database constraint protects eventId;
- [ ] concurrent duplicate cannot apply projection twice.

### Transactionality

- [ ] projection and processed event commit together;
- [ ] projection failure rolls back processed-event registration;
- [ ] processed-event failure rolls back projection change;
- [ ] failed processing leaves event logically unprocessed.

### Out-of-order

- [ ] `BET_UPDATED` before `BET_CREATED` fails without projection creation.
- [ ] `BET_SETTLED` before `BET_CREATED` fails without projection creation.
- [ ] failed out-of-order event is not marked processed.
- [ ] existing projection remains unchanged after rejected out-of-order/conflicting event.

## Expected tests

### Application unit tests

Use mocks/fakes for Analytics persistence ports.

Do not require:

- RabbitMQ;
- PostgreSQL;
- Spring Context;
- JPA;
- Testcontainers.

Unit tests should cover:

- event dispatch;
- envelope validation where owned by application;
- `BET_CREATED` orchestration;
- `BET_UPDATED` orchestration;
- `BET_SETTLED` orchestration;
- duplicate short-circuit;
- missing projection behavior;
- unknown event behavior;
- invalid lifecycle payload behavior;
- no processed-event registration after failure.

Application unit tests must not prescribe JPA implementation details.

### Projection repository integration tests

Use the project's PostgreSQL/Testcontainers integration strategy.

Validate directly:

```text
analytics_bets
processed_events
```

including:

- projection save/reload;
- `betId` uniqueness;
- decimal precision;
- nullable settlement fields;
- update persistence;
- settlement persistence;
- `eventId` uniqueness;
- processed-event persistence;
- transaction rollback behavior.

Do not access Betting persistence.

### Consumer integration tests

Use RabbitMQ plus Analytics persistence according to the project's approved integration testing strategy.

Publish synthetic valid version-one events through:

```text
betting.events
```

using the Task 6.1 routing keys and prove delivery through:

```text
analytics.betting-events.queue
```

Verify:

```text
message
↓
consumer
↓
analytics projection
↓
processed event
```

At minimum cover:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
duplicate eventId
malformed/invalid event
```

Do not require the Betting Service to be running.

Task 6.2 already owns producer behavior.

### Full lifecycle consumer integration

Where practical, run a sequence:

```text
BET_CREATED
↓
BET_UPDATED
↓
BET_SETTLED
```

for the same:

```text
betId
```

using distinct:

```text
eventId
```

and verify the final Analytics projection.

Then redeliver one or more of the same event IDs and verify the final projection is unchanged.

### Concurrency/idempotency integration

At least one persistence/integration test should prove that durable uniqueness prevents the same event ID from being committed twice.

Do not rely solely on Mockito verification for the concurrency guarantee.

### Regression

All existing tests from:

- Phase 5;
- Task 6.1;
- Task 6.2;

must remain Green.

Task 6.3 must not require dashboard implementation.

## Protected behavior

Task 6.3 must preserve the version-one messaging contract created in Task 6.1.

Do not alter event payloads merely to simplify Analytics consumption.

Do not alter Task 6.2 publisher payloads merely to make a consumer test easier.

If the version-one payload is insufficient for a required projection operation, stop and report the contract gap instead of silently fetching data from Betting or adding undocumented fields.

## Out of scope

Task 6.3 must not implement:

- dashboard endpoints;
- bankroll evolution endpoint;
- performance breakdown endpoint;
- synchronous Betting Service lookup;
- Betting database access;
- writes to `betting_db`;
- transactional outbox;
- publisher retries;
- consumer retry policy beyond minimum existing framework behavior;
- custom retry loops;
- dead-letter exchange;
- dead-letter queue;
- manual event replay;
- event correction;
- event reorder buffer;
- global strict ordering;
- multi-version event migration;
- event schema registry;
- notification consumers;
- recommendation consumers;
- delete events;
- Phase 7 functionality.

## Dependencies

- Task 6.1 — messaging contract and RabbitMQ topology.
- Task 6.2 — lifecycle event publisher.
- Analytics Service skeleton from Phase 2.
- RabbitMQ infrastructure from Phase 1.
- PostgreSQL infrastructure from Phase 1.
- `docs/events.md`.

The Analytics consumer can be integration-tested with synthetic version-one messages and does not require the Betting Service process to run.

## Definition of Done

Apply `docs/definition-of-done.md`.

Additionally:

- Analytics migration exists for required projection/idempotency tables if necessary;
- all three version-one lifecycle events are consumed;
- valid events update Analytics projections correctly;
- duplicate event IDs never apply projection changes twice;
- idempotency survives application restart because it is persisted;
- processed-event uniqueness exists in the database;
- projection mutation and processed-event registration are transactional;
- invalid events do not partially mutate projections;
- failed events are not marked processed;
- out-of-order update/settlement without projection fail safely;
- Analytics never accesses Betting persistence;
- financial values remain `BigDecimal`;
- Task 6.1 tests remain Green;
- Task 6.2 tests remain Green;
- relevant Analytics tests remain Green;
- RabbitMQ/PostgreSQL integration tests pass;
- `git diff --check` passes;
- no credentials or environment-specific secrets are committed;
- no DLQ/retry/replay/dashboard scope is introduced.

## Status and evidence

Current status:

```text
PLANNED
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