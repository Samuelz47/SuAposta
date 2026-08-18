# Events

## 1. Overview

This document defines the asynchronous event contracts used by the platform.

RabbitMQ is used to propagate betting lifecycle changes from the Betting Service to the Analytics Service.

The first version of the system uses the following event-driven flow:

```text
Betting Service
  ↓ persists business state
betting_db
  ↓ publishes lifecycle event
RabbitMQ
  ↓ delivers event
Analytics Service
  ↓ consumes event and updates projection
analytics_db
```

The Betting Service is the producer of betting lifecycle events.

The Analytics Service is the consumer of betting lifecycle events.

The initial asynchronous contract is implemented through:

```text
Task 6.1
Messaging contract and RabbitMQ topology

Task 6.2
Betting lifecycle event publication

Task 6.3
Idempotent Analytics event consumption
```

---

## 2. Goals

The event system must:

- Keep the Betting Service and Analytics Service decoupled.
- Allow dashboard data to be updated asynchronously.
- Avoid direct database access between services.
- Provide stable contracts for AI agents and developers.
- Support future evolution through event versioning.
- Preserve Betting-owned business calculations.
- Make event consumption idempotent.
- Keep the first implementation simple enough for the current project scope.

The initial version does not attempt to provide distributed exactly-once delivery.

---

## 3. RabbitMQ Topology

## 3.1 Exchange

Initial exchange:

```text
betting.events
```

Type:

```text
topic
```

The topic exchange routes messages using lifecycle routing keys.

Initial routing examples:

```text
bet.created
bet.updated
bet.settled
```

Only this exchange is part of the initial Betting lifecycle contract.

Additional betting exchanges must not be introduced unless explicitly documented.

---

## 3.2 Queue

Initial Analytics queue:

```text
analytics.betting-events.queue
```

The Analytics Service consumes all initial Betting lifecycle events from this queue.

The first version does not use one queue per event type.

---

## 3.3 Routing Keys

Initial routing keys:

```text
bet.created
bet.updated
bet.settled
```

Mapping:

| Event type | Routing key |
| --- | --- |
| `BET_CREATED` | `bet.created` |
| `BET_UPDATED` | `bet.updated` |
| `BET_SETTLED` | `bet.settled` |

Future routing keys may include:

```text
bet.deleted
bet.corrected
```

Future contracts may also introduce other lifecycle-specific routes when explicitly required.

The first version must not implement future routing keys unless explicitly requested.

`CANCELLED` is a settlement status in the current Bet lifecycle.

Therefore:

```text
status = CANCELLED
```

is published as:

```text
eventType = BET_SETTLED
routingKey = bet.settled
```

The initial version must not create:

```text
BET_CANCELLED
bet.cancelled
```

as a separate lifecycle event.

---

## 3.4 Bindings

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

This allows the Analytics Service to receive all initial Betting lifecycle events.

An unrelated routing key must not match these bindings.

---

## 3.5 Undocumented topology properties

The version-one public contract defines:

- exchange name;
- exchange type;
- queue name;
- routing keys;
- bindings.

Properties not explicitly defined by this document, such as:

- durability;
- exclusivity;
- auto-delete;
- queue arguments;
- exchange arguments;

are implementation details unless later promoted into the documented contract.

Implementations should use stable RabbitMQ/Spring configuration appropriate for a restartable application environment.

Tests must not invent an exact public requirement for an undocumented RabbitMQ property merely to constrain the implementation.

---

## 4. Event Naming

Event type names use uppercase snake case.

Initial values:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

Routing keys use lowercase dot notation.

Initial values:

```text
bet.created
bet.updated
bet.settled
```

Java class names should use PascalCase.

Examples:

```text
BetCreatedEvent
BetUpdatedEvent
BetSettledEvent
```

Payload classes should remain explicit.

Examples:

```text
BetCreatedPayload
BetUpdatedPayload
BetSettledPayload
```

Exact Java class names are implementation details provided they represent the documented contract without changing its semantics.

---

## 5. Event Envelope

All version-one Betting events use the same logical envelope:

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

---

## 5.1 Envelope Fields

### eventId

Unique identifier for the event occurrence.

Type:

```text
UUID
```

Required:

```text
yes
```

Purpose:

- Traceability.
- Idempotency.
- Debugging.
- Duplicate-delivery detection.

`eventId` identifies an event, not a Bet.

Different lifecycle events for the same Bet must be able to use different event IDs.

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

Do not reuse `betId` as `eventId`.

---

### eventType

Business event type.

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

An unknown event type is not a valid processable version-one Betting lifecycle event.

---

### occurredAt

Date and time of the persisted lifecycle operation represented by the event.

Type:

```text
ISO-8601 instant
```

Java representation:

```text
Instant
```

Required:

```text
yes
```

Example:

```text
2026-07-21T21:00:00Z
```

Publisher mapping:

```text
BET_CREATED
occurredAt = persisted Bet createdAt

BET_UPDATED
occurredAt = persisted Bet updatedAt

BET_SETTLED
occurredAt = persisted Bet updatedAt
```

The publisher must use the lifecycle timestamp from the successfully persisted Bet when that authoritative timestamp already exists.

Do not generate a second unrelated timestamp merely for messaging.

---

### version

Event contract version.

Type:

```text
integer
```

Required:

```text
yes
```

Initial value:

```text
1
```

The version-one consumer processes only:

```text
version = 1
```

---

### producer

Name of the service that published the event.

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

The Analytics version-one Betting consumer must not process a Betting lifecycle event using another producer value.

---

### payload

Event-specific data.

Serialized JSON type:

```text
object
```

Required:

```text
yes
```

The payload must be explicit for the corresponding event type.

A missing or null payload is not a valid processable version-one event.

The payload must not be serialized as an opaque JSON string.

---

## 5.2 Version-one envelope validity

A processable version-one Betting envelope requires:

```text
eventId != null
eventType != null
occurredAt != null
payload != null

version = 1
producer = betting-service
```

and:

```text
eventType in:
BET_CREATED
BET_UPDATED
BET_SETTLED
```

Invalid envelopes must not produce Analytics projection changes.

Invalid envelopes must not be registered as successfully processed events.

---

## 6. Event Contracts

## 6.1 BetCreatedEvent

Published when a new Bet is successfully created and persisted in the Betting Service.

### Producer

```text
betting-service
```

### Consumer

```text
analytics-service
```

### Exchange

```text
betting.events
```

### Routing key

```text
bet.created
```

### Event type

```text
BET_CREATED
```

### When to publish

Publish only after the new Bet is successfully persisted in:

```text
betting_db
```

The event must be constructed from the successfully persisted Bet.

If persistence fails:

```text
BET_CREATED must not be published
```

### occurredAt

```text
occurredAt = persisted Bet createdAt
```

### Expected consumer behavior

The Analytics Service creates the initial projection record in:

```text
analytics_bets
```

A duplicate delivery of the same `eventId` is ignored idempotently.

A new `BET_CREATED` with a different `eventId` for a `betId` whose projection already exists is a projection lifecycle conflict and must not silently overwrite the projection.

### Payload

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

Payload types:

```text
betId     -> UUID
userId    -> UUID
odds      -> BigDecimal
stake     -> BigDecimal
placedAt  -> Instant
```

The payload must reflect persisted normalized Bet values.

### Full example

```json
{
  "eventId": "3df04e41-6a77-4c8e-9c6f-b663d68c1c92",
  "eventType": "BET_CREATED",
  "occurredAt": "2026-07-21T21:00:00Z",
  "version": 1,
  "producer": "betting-service",
  "payload": {
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
}
```

---

## 6.2 BetUpdatedEvent

Published when an existing owned `PENDING` Bet is successfully updated and persisted.

This event represents editable Bet changes only.

Settlement must use `BET_SETTLED`.

### Producer

```text
betting-service
```

### Consumer

```text
analytics-service
```

### Exchange

```text
betting.events
```

### Routing key

```text
bet.updated
```

### Event type

```text
BET_UPDATED
```

### When to publish

Publish after editable Bet fields are successfully persisted in:

```text
betting_db
```

Examples of editable fields include:

- Sport.
- League.
- Teams.
- Market.
- Selection.
- Odds.
- Stake.
- Placed date.
- Notes.

This event must not be used for settlement.

If persistence fails:

```text
BET_UPDATED must not be published
```

### occurredAt

```text
occurredAt = persisted Bet updatedAt
```

### Expected consumer behavior

The Analytics Service updates an existing projection record in:

```text
analytics_bets
```

If no projection exists for the event `betId`:

```text
processing fails
projection is not created
eventId is not marked processed
```

### Payload

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

Payload types:

```text
betId      -> UUID
userId     -> UUID
odds       -> BigDecimal
stake      -> BigDecimal
placedAt   -> Instant
updatedAt  -> Instant
```

### Full example

```json
{
  "eventId": "ef4d9e79-6d77-4fb9-a417-2053ca2df403",
  "eventType": "BET_UPDATED",
  "occurredAt": "2026-07-21T21:20:00Z",
  "version": 1,
  "producer": "betting-service",
  "payload": {
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
}
```

---

## 6.3 BetSettledEvent

Published when a Bet is successfully transitioned from `PENDING` into a final settlement state and that state is persisted.

### Producer

```text
betting-service
```

### Consumer

```text
analytics-service
```

### Exchange

```text
betting.events
```

### Routing key

```text
bet.settled
```

### Event type

```text
BET_SETTLED
```

### When to publish

Publish after the Bet settlement is successfully persisted in:

```text
betting_db
```

Settlement statuses:

```text
WON
LOST
VOID
CASHOUT
CANCELLED
```

All five statuses use the same:

```text
BET_SETTLED
bet.settled
```

contract.

If settlement or persistence fails:

```text
BET_SETTLED must not be published
```

### occurredAt

```text
occurredAt = persisted Bet updatedAt
```

The current Betting lifecycle sets `settledAt` and `updatedAt` from the same server-controlled operation instant during successful settlement.

The payload still carries `settledAt` explicitly because it is a business settlement field.

### Expected consumer behavior

The Analytics Service updates an existing projection record in:

```text
analytics_bets
```

Settlement updates include:

- Status.
- Profit.
- Return amount.
- Settled date.
- Updated date.

If no projection exists:

```text
processing fails
projection is not created
eventId is not marked processed
```

The settlement event does not contain a complete Bet snapshot and must not be used to synthesize a partial Analytics projection.

### Payload

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

Payload types:

```text
betId         -> UUID
userId        -> UUID
odds          -> BigDecimal
stake         -> BigDecimal
profit        -> BigDecimal
returnAmount  -> BigDecimal
settledAt     -> Instant
```

### Full example

```json
{
  "eventId": "b544ff65-e4d0-428e-b70f-c3732c696f2e",
  "eventType": "BET_SETTLED",
  "occurredAt": "2026-07-21T22:00:00Z",
  "version": 1,
  "producer": "betting-service",
  "payload": {
    "betId": "f8c6eb32-54d4-4024-9581-7a0a8d6f4f19",
    "userId": "b40da580-a017-4a11-bd42-c67aa6409166",
    "status": "WON",
    "odds": 2.10,
    "stake": 100.00,
    "profit": 110.00,
    "returnAmount": 210.00,
    "settledAt": "2026-07-21T22:00:00Z"
  }
}
```

---

## 7. Profit and Return Rules

The Betting Service is solely responsible for calculating Bet-level profit and return amount before publishing settlement events.

Messaging and Analytics must not independently recalculate financial results.

Financial calculations use the domain behavior established for Bets.

Money values use:

```text
scale = 2
RoundingMode = HALF_UP
```

Odds are represented using `BigDecimal` and preserve the Betting domain precision.

## 7.1 WON

```text
returnAmount = stake * odds
profit = returnAmount - stake
```

The return amount is normalized to monetary scale before the final profit calculation.

Example:

```text
stake = 100.00
odds = 2.10

returnAmount = 210.00
profit = 110.00
```

---

## 7.2 LOST

```text
returnAmount = 0.00
profit = -stake
```

Example:

```text
stake = 100.00

returnAmount = 0.00
profit = -100.00
```

---

## 7.3 VOID

```text
returnAmount = stake
profit = 0.00
```

Example:

```text
stake = 100.00

returnAmount = 100.00
profit = 0.00
```

---

## 7.4 CASHOUT

For CASHOUT, the client supplies:

```text
returnAmount
```

The Betting domain normalizes the supplied return amount using:

```text
scale = 2
RoundingMode = HALF_UP
```

and calculates:

```text
profit = returnAmount - stake
```

CASHOUT may produce:

- Positive profit.
- Zero profit.
- Negative profit.

Example:

```text
stake = 100.00
cashout returnAmount = 130.00

profit = 30.00
```

Rounding example:

```text
stake = 100.00
cashout returnAmount input = 80.126

normalized returnAmount = 80.13
profit = -19.87
```

The published event contains the normalized values already persisted by the Betting Service.

---

## 7.5 CANCELLED

```text
returnAmount = stake
profit = 0.00
```

Example:

```text
stake = 100.00

returnAmount = 100.00
profit = 0.00
```

A cancelled Bet should generally not affect performance metrics as a win or loss.

The exact Analytics metric treatment belongs to Analytics behavior rather than messaging calculation.

---

## 8. Idempotency

Consumers must be idempotent.

The Analytics Service persists processed event IDs using a table equivalent to:

```text
processed_events
```

Minimum fields:

```text
event_id
event_type
processed_at
```

`event_id` must have a durable uniqueness constraint.

Idempotency is based on:

```text
eventId
```

not:

```text
betId
```

Different events referring to the same Bet are independently processable.

---

## 8.1 Duplicate event behavior

If an `eventId` has already been successfully processed:

```text
do not apply projection mutation again
do not create another processed-event record
complete consumer processing successfully
```

Conceptually:

```text
event received
  ↓
eventId already processed?
  ↓ yes
ignore safely
  ↓
successful completion
```

Duplicate delivery is expected messaging behavior and is not a business error.

---

## 8.2 Durable idempotency

A process-local collection such as:

```text
Set<UUID>
```

is not sufficient.

Idempotency must survive application restarts through Analytics persistence.

A database uniqueness constraint on:

```text
processed_events.event_id
```

is the final concurrency-safe guard.

An application-level pre-check alone is not sufficient under concurrent delivery.

---

## 8.3 Transactional processing

For a new valid event:

```text
projection mutation
+
processed-event registration
```

must occur in one Analytics database transaction.

Required outcome:

```text
both commit
or
neither commits
```

The system must not leave:

```text
projection changed
eventId not registered
```

or:

```text
eventId registered
projection change rolled back
```

This transaction covers only:

```text
analytics_db
```

It is not a RabbitMQ/database distributed transaction.

---

## 9. Ordering

The first version does not guarantee strict global event ordering.

The expected normal lifecycle order is:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

The initial consumer does not implement an event reorder buffer.

---

## 9.1 BET_UPDATED before BET_CREATED

If:

```text
BET_UPDATED
```

arrives for a `betId` without an existing Analytics projection:

```text
processing fails
projection is not created
eventId is not marked processed
```

The update payload is not a complete Bet snapshot and must not be used to synthesize the missing projection.

---

## 9.2 BET_SETTLED before BET_CREATED

If:

```text
BET_SETTLED
```

arrives for a `betId` without an existing Analytics projection:

```text
processing fails
projection is not created
eventId is not marked processed
```

The settlement payload is not a complete Bet snapshot and must not be used to synthesize the missing projection.

---

## 9.3 Unexpected second BET_CREATED

If an Analytics projection already exists for:

```text
betId = X
```

and a different, previously unprocessed:

```text
eventId
```

attempts another:

```text
BET_CREATED
```

for that same Bet:

```text
processing fails
existing projection remains unchanged
eventId is not marked processed
```

This is different from receiving the same `eventId` again.

The same `eventId` is a duplicate and is ignored idempotently.

---

## 9.4 Stale lifecycle events

The first version does not implement a general timestamp-based conflict resolution or event-sequence engine.

If an event cannot safely be applied to the current projection lifecycle:

```text
fail processing
do not corrupt projection state
do not mark eventId processed
```

Advanced ordering and replay strategies belong to future improvements.

---

## 10. Error Handling

Event processing failures must not be silently ignored.

A failure must leave the event logically unprocessed.

Examples:

- Malformed JSON.
- Invalid envelope.
- Unknown event type.
- Unsupported version.
- Invalid producer.
- Invalid payload.
- Missing projection for update.
- Missing projection for settlement.
- Projection persistence failure.
- Processed-event persistence failure.

For these failures:

```text
analytics_bets must not be partially mutated
processed_events must not record successful processing
failure must propagate to messaging infrastructure
```

Task 6.3 does not introduce an advanced custom retry policy.

Task 6.3 does not introduce a dead-letter queue.

Task 6.3 does not introduce a dead-letter exchange.

Task 6.3 does not manually loop retries.

RabbitMQ/Spring infrastructure may handle a propagated failure according to its configured transport behavior.

Failed messages must not be silently discarded by application logic.

Future behavior may include:

- Explicit retry configuration.
- Dead-letter exchange.
- Dead-letter queue.
- Manual reprocessing.
- Event replay.

Suggested future names remain:

```text
betting.events.dlx
analytics.betting-events.dlq
```

These are not part of the initial topology.

---

## 11. Event Versioning

All events must include:

```text
version
```

Initial version:

```text
1
```

The initial Analytics consumer supports:

```text
version = 1
```

An event with another version must not be processed by the version-one consumer.

Versioning rules:

- Non-breaking additions may keep the same version when compatible with existing consumers.
- Breaking changes require a new version.
- Do not remove fields from an existing event version.
- Do not change the meaning of an existing field.
- Do not change field types without a new version.

Examples of breaking changes:

- Renaming `betId`.
- Changing `stake` from number to string.
- Removing `userId`.
- Changing existing field semantics.
- Changing supported status semantics.

Multi-version dispatch is outside the initial implementation.

---

## 12. Serialization

Events are serialized as:

```text
JSON
```

Date/time fields use ISO-8601 instants.

Example:

```text
2026-07-21T22:00:00Z
```

Java should represent business timestamps using:

```text
Instant
```

Financial and odds fields are serialized as JSON numbers.

Java must use:

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

for financial or odds event values.

Serialization/deserialization must not introduce binary floating-point conversion.

Trailing zero formatting in JSON is not itself part of the numeric contract.

For example:

```text
2.1
2.10
2.1000
```

may represent the same numeric value provided the expected decimal value is preserved after deserialization.

Malformed JSON may fail safely at the serialization boundary.

Malformed data must not produce a valid partial event.

---

## 13. Security and Privacy

Events must contain only data required by documented consumers.

Events must not include:

- User password.
- Password hash.
- User email unless explicitly required by a future contract.
- JWT.
- Authorization header.
- Refresh token.
- `X-User-Id` header.
- Payment data.
- Database credentials.
- HTTP request objects.
- Internal persistence entities.
- Stack traces.
- Internal exception details.
- Sensitive personal information not explicitly required by the contract.

The initial version identifies users only through:

```text
userId
```

Events must not serialize JPA entities directly.

---

## 14. Analytics Projection

The Analytics Service maintains a local projection table:

```text
analytics_bets
```

The projection belongs exclusively to:

```text
analytics_db
```

Suggested fields:

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

The business identity of the projection is:

```text
bet_id
```

One Bet must correspond to at most one active projection record.

`bet_id` therefore requires an appropriate uniqueness rule.

Financial database fields must use decimal database types.

Do not persist event financial values using floating-point database types.

Dashboard queries must read from:

```text
analytics_db
```

and must not read directly from the Betting Service database.

Analytics must never:

- query `betting_db`;
- write `betting_db`;
- use Betting repositories;
- use Betting JPA entities;
- perform cross-database joins with Betting persistence.

The only initial Betting-to-Analytics state propagation is:

```text
RabbitMQ lifecycle events
```

---

## 14.1 Projection timestamps

Analytics projection timestamps represent Betting lifecycle information carried by events.

They are different from Analytics processing time.

### BET_CREATED

Because the version-one creation payload does not contain dedicated `createdAt` or `updatedAt` fields:

```text
analytics_bets.created_at = envelope.occurredAt
analytics_bets.updated_at = envelope.occurredAt
```

### BET_UPDATED

```text
analytics_bets.updated_at = payload.updatedAt
```

The existing:

```text
created_at
```

must be preserved.

### BET_SETTLED

The version-one settlement payload contains:

```text
settledAt
```

but not a separate:

```text
updatedAt
```

Therefore:

```text
analytics_bets.settled_at = payload.settledAt
analytics_bets.updated_at = envelope.occurredAt
```

The current Betting lifecycle normally produces both timestamps from the same operation instant, but consumers must map each contract field according to its documented source.

---

## 15. Consumer Behavior by Event

## 15.1 BET_CREATED

Action:

```text
Create analytics_bets projection.
```

Initial lifecycle state:

```text
status = PENDING
profit = null
return_amount = null
settled_at = null
```

Projection values come from the event payload.

Analytics must not independently normalize or recalculate Betting business values.

If the same `eventId` was already processed:

```text
ignore duplicate successfully
```

If the projection already exists but the incoming `eventId` is new:

```text
fail processing
preserve existing projection
do not register eventId
```

---

## 15.2 BET_UPDATED

Action:

```text
Update existing analytics_bets projection.
```

Editable projection fields:

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

The expected status remains:

```text
PENDING
```

This event must not alter settlement fields.

If the projection does not exist:

```text
fail processing
do not create projection
do not register eventId
```

---

## 15.3 BET_SETTLED

Action:

```text
Update settlement fields in existing analytics_bets projection.
```

Settlement fields:

```text
status
profit
return_amount
settled_at
updated_at
```

Valid settlement statuses:

```text
WON
LOST
VOID
CASHOUT
CANCELLED
```

A settlement payload using:

```text
PENDING
```

is invalid.

Financial values must be persisted exactly from the event.

Analytics must not calculate:

```text
profit
returnAmount
```

again.

If the projection does not exist:

```text
fail processing
do not create projection
do not register eventId
```

---

## 16. Publisher Guidelines

The Betting Service must publish a lifecycle event only after the related database persistence succeeds.

The required sequence is:

```text
validate operation
  ↓
apply domain behavior
  ↓
persist Bet
  ↓
persistence succeeds
  ↓
build event from persisted Bet
  ↓
publish event
```

The source of event payload values is:

```text
successfully persisted Bet
```

not:

```text
HTTP request body
```

Published events must therefore contain persisted:

- Ownership.
- Status.
- Normalized Stake.
- Normalized Odds.
- Domain-calculated Profit.
- Domain-calculated Return Amount.
- Server-controlled timestamps.

The application layer must publish through a messaging output port.

RabbitMQ-specific APIs belong in infrastructure.

The Bet domain must not depend on messaging.

---

## 16.1 Publication mapping

Successful creation:

```text
BET_CREATED
bet.created
```

Successful editable update:

```text
BET_UPDATED
bet.updated
```

Successful settlement:

```text
BET_SETTLED
bet.settled
```

A successful settlement must not additionally publish:

```text
BET_UPDATED
```

---

## 16.2 Persistence failure

If persistence fails:

```text
publisher must not be invoked
```

This applies to:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

An event must never advertise state that failed to persist.

---

## 16.3 Failure before persistence

If an operation fails because of:

- Domain validation.
- Lifecycle validation.
- Missing Bet.
- Cross-user lookup.
- Invalid Stake.
- Invalid Odds.
- Missing CASHOUT return amount.
- Other application validation.

then:

```text
no lifecycle event is published
```

---

## 16.4 Publication failure after persistence

The first version uses:

```text
database persistence
then
direct RabbitMQ publication
```

Therefore PostgreSQL and RabbitMQ are not atomically committed together.

If publication fails after database persistence succeeds:

- The persisted Bet remains authoritative.
- The publication failure must remain observable.
- Messaging code must not claim successful delivery.
- The database operation must not be manually reverted.
- No fallback lifecycle event should be published.
- No retry loop should be invented by application code.

This limitation is accepted in the first version.

Future improvement:

```text
Transactional Outbox Pattern
```

Agents must not implement the outbox pattern unless explicitly requested.

---

## 17. Consumer Guidelines

The Analytics Service must:

- Consume events from `analytics.betting-events.queue`.
- Deserialize the version-one contract.
- Validate the envelope.
- Validate the event payload.
- Check idempotency using `eventId`.
- Short-circuit successfully when the same `eventId` was already processed.
- Apply the event-specific projection change.
- Store the processed event.
- Commit projection mutation and processed-event registration atomically.
- Propagate processing failures to messaging infrastructure.
- Avoid direct Betting database access.

The RabbitMQ listener should remain thin:

```text
receive
deserialize
delegate
```

Application logic should own:

```text
idempotency
event dispatch
projection orchestration
transactional processing
```

Persistence adapters should own:

```text
database implementation
```

---

## 17.1 Successful consumer completion

Consumer processing completes successfully when:

```text
a new valid event is applied and committed
```

or:

```text
the same eventId was already processed and is safely ignored
```

---

## 17.2 Consumer failure

Consumer processing fails when:

```text
event JSON is malformed
envelope is invalid
eventType is unknown
version is unsupported
producer is invalid
payload is invalid
required projection does not exist
projection persistence fails
processed-event persistence fails
```

Failed processing must not mark the event as processed.

---

## 17.3 Processed event persistence

The Analytics Service stores processed events in:

```text
processed_events
```

Minimum fields:

```text
event_id
event_type
processed_at
```

`processed_at` represents Analytics processing time.

It is not a substitute for:

```text
occurredAt
createdAt
updatedAt
settledAt
```

Where Analytics generates `processedAt`, application code should use a deterministic time source such as:

```text
Clock
```

with production configuration equivalent to:

```text
Clock.systemUTC()
```

---

## 18. Local Development

RabbitMQ is available through Docker Compose.

Local ports:

```text
5672:5672
15672:15672
```

Port:

```text
5672
```

is used by applications for AMQP communication.

Port:

```text
15672
```

is used by RabbitMQ Management UI.

Local development credentials may use:

```text
guest / guest
```

when compatible with the local RabbitMQ environment.

These credentials must not be hardcoded for production.

Application connection settings must remain configuration/environment driven.

---

## 19. Initial RabbitMQ Constants

The version-one contract uses exactly:

```text
Exchange:
betting.events

Queue:
analytics.betting-events.queue

Routing keys:
BET_CREATED -> bet.created
BET_UPDATED -> bet.updated
BET_SETTLED -> bet.settled

Event types:
BET_CREATED
BET_UPDATED
BET_SETTLED

Producer:
betting-service

Version:
1
```

These values should have a stable representation in code.

Avoid duplicating unrelated string literals across the messaging implementation.

If shared messaging code is introduced, it must remain a neutral messaging contract boundary and must not couple Analytics to Betting implementation classes.

---

## 20. Initial Failure Decisions

The first-version consumer behavior for exceptional messages is explicit.

### Duplicate eventId

```text
ignore safely
do not mutate projection again
do not create another processed-event row
complete successfully
```

### Unknown event type

```text
fail processing
do not mutate projection
do not mark processed
```

### Unsupported version

```text
fail processing
do not mutate projection
do not mark processed
```

### Invalid producer

```text
fail processing
do not mutate projection
do not mark processed
```

### Malformed envelope

```text
fail processing
do not mutate projection
do not mark processed
```

### Invalid payload

```text
fail processing
do not mutate projection
do not mark processed
```

### BET_UPDATED without existing projection

```text
fail processing
do not create projection
do not mark processed
```

### BET_SETTLED without existing projection

```text
fail processing
do not create projection
do not mark processed
```

### New BET_CREATED for existing projection

```text
fail processing
preserve existing projection
do not mark processed
```

### Projection persistence failure

```text
rollback Analytics transaction
do not mark processed
propagate failure
```

### Processed-event persistence failure

```text
rollback projection mutation
do not treat event as processed
propagate failure
```

The first version does not define advanced retry, DLQ, replay, or reorder behavior beyond these application decisions.

---

## 21. Initial Transaction Boundary

For a valid new event, Analytics processing must conceptually perform:

```text
BEGIN analytics transaction

check durable idempotency
apply projection mutation
store processed eventId

COMMIT
```

Required invariant:

```text
projection mutation
and
processed-event registration
```

must commit together.

If either fails:

```text
ROLLBACK
```

This protects against duplicate projection mutation during RabbitMQ redelivery.

RabbitMQ acknowledgement itself is not part of the Analytics database transaction.

Distributed RabbitMQ/PostgreSQL exactly-once processing is not guaranteed.

---

## 22. Future Improvements

Possible future improvements:

- Dead-letter queue.
- Dead-letter exchange.
- Explicit retry configuration.
- Transactional outbox.
- Event schema registry or schema validation.
- Full Bet snapshot in all lifecycle events.
- Event replay.
- Audit log.
- Event correction flow.
- Event sequence numbers.
- More tolerant out-of-order processing.
- Separate queues per event type.
- Multiple consumers for notification or recommendation services.
- Multi-version event dispatch.
- Manual reprocessing tools.
- Observability and messaging metrics.

These improvements are out of scope for the initial Phase 6 implementation unless explicitly promoted into a task specification.