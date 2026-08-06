# Events

## 1. Overview

This document defines the asynchronous event contracts used by the platform.

RabbitMQ is used to propagate betting changes from the Betting Service to the Analytics Service.

The first version of the system uses a simple event-driven flow:

```text
Betting Service
  ↓ publishes events
RabbitMQ
  ↓ delivers events
Analytics Service
  ↓ consumes events and updates projections
analytics_db
```

The Betting Service is the producer of betting events.

The Analytics Service is the consumer of betting events.

---

## 2. Goals

The event system must:

- Keep the Betting Service and Analytics Service decoupled.
- Allow dashboard data to be updated asynchronously.
- Avoid direct database access between services.
- Provide stable contracts for AI agents and developers.
- Support future evolution through event versioning.
- Keep the first implementation simple.

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

Reason:

A topic exchange allows routing by event type using routing keys such as:

```text
bet.created
bet.updated
bet.settled
```

---

## 3.2 Queue

Initial analytics queue:

```text
analytics.betting-events.queue
```

Reason:

The first version should keep consumption simple.

The Analytics Service will consume all relevant betting events from one queue.

---

## 3.3 Routing Keys

Initial routing keys:

```text
bet.created
bet.updated
bet.settled
```

Future routing keys may include:

```text
bet.cancelled
bet.deleted
bet.corrected
```

The first version should not implement future routing keys unless explicitly requested.

---

## 3.4 Bindings

The queue `analytics.betting-events.queue` should be bound to the exchange `betting.events` using:

```text
bet.created
bet.updated
bet.settled
```

This means the Analytics Service receives all initial betting lifecycle events.

---

## 4. Event Naming

Event type names should use uppercase snake case.

Examples:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

Routing keys should use lowercase dot notation.

Examples:

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

Payload classes should be explicit.

Examples:

```text
BetCreatedPayload
BetUpdatedPayload
BetSettledPayload
```

---

## 5. Event Envelope

All events must use the same envelope structure.

```json
{
  "eventId": "uuid",
  "eventType": "BET_CREATED",
  "occurredAt": "2026-07-21T21:00:00Z",
  "version": 1,
  "producer": "betting-service",
  "payload": {}
}
```

## 5.1 Envelope Fields

### eventId

Unique event identifier.

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
- Avoiding duplicate processing.

---

### eventType

Business event type.

Type:

```text
string
```

Required:

```text
yes
```

Allowed initial values:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

---

### occurredAt

Date and time when the event happened.

Type:

```text
ISO-8601 instant
```

Required:

```text
yes
```

Example:

```text
2026-07-21T21:00:00Z
```

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

---

### payload

Event-specific data.

Type:

```text
object
```

Required:

```text
yes
```

---

## 6. Event Contracts

---

## 6.1 BetCreatedEvent

Published when a new bet is created in the Betting Service.

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

Publish after the bet is successfully persisted in `betting_db`.

### Expected consumer behavior

The Analytics Service should create or update a projection record in `analytics_bets`.

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
  "odds": 2.1,
  "stake": 100.00,
  "status": "PENDING",
  "placedAt": "2026-07-21T20:30:00Z"
}
```

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
    "odds": 2.1,
    "stake": 100.00,
    "status": "PENDING",
    "placedAt": "2026-07-21T20:30:00Z"
  }
}
```

---

## 6.2 BetUpdatedEvent

Published when an existing bet is updated but not settled.

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

Publish after editable bet fields are successfully updated in `betting_db`.

This event should be used for updates such as:

- Sport
- League
- Teams
- Market
- Selection
- Odds
- Stake
- Placed date
- Notes

This event should not be used for settlement. Settlement must publish `BET_SETTLED`.

### Expected consumer behavior

The Analytics Service should update the existing projection record in `analytics_bets`.

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

Published when a bet receives a final or financially relevant result.

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

Publish after the bet settlement is successfully persisted in `betting_db`.

Settlement statuses:

```text
WON
LOST
VOID
CASHOUT
CANCELLED
```

### Expected consumer behavior

The Analytics Service should update the projection record in `analytics_bets`, including:

- status
- profit
- return amount
- settled date

Dashboard metrics should then reflect the new settled state.

### Payload

```json
{
  "betId": "f8c6eb32-54d4-4024-9581-7a0a8d6f4f19",
  "userId": "b40da580-a017-4a11-bd42-c67aa6409166",
  "status": "WON",
  "odds": 2.1,
  "stake": 100.00,
  "profit": 110.00,
  "returnAmount": 210.00,
  "settledAt": "2026-07-21T22:00:00Z"
}
```

### Full example

```json
{
  "eventId": "b544ff65-e4d0-428e-b70f-c3732c696f2e",
  "eventType": "BET_SETTLED",
  "occurredAt": "2026-07-21T22:10:00Z",
  "version": 1,
  "producer": "betting-service",
  "payload": {
    "betId": "f8c6eb32-54d4-4024-9581-7a0a8d6f4f19",
    "userId": "b40da580-a017-4a11-bd42-c67aa6409166",
    "status": "WON",
    "odds": 2.1,
    "stake": 100.00,
    "profit": 110.00,
    "returnAmount": 210.00,
    "settledAt": "2026-07-21T22:00:00Z"
  }
}
```

---

## 7. Profit and Return Rules

The Betting Service is responsible for calculating bet-level profit and return amount before publishing settlement events.

### WON

```text
profit = stake * (odds - 1)
returnAmount = stake * odds
```

Example:

```text
stake = 100
odds = 2.10
profit = 110
returnAmount = 210
```

### LOST

```text
profit = -stake
returnAmount = 0
```

Example:

```text
stake = 100
odds = 2.10
profit = -100
returnAmount = 0
```

### VOID

```text
profit = 0
returnAmount = stake
```

Example:

```text
stake = 100
odds = 2.10
profit = 0
returnAmount = 100
```

### CASHOUT

```text
profit = custom value informed by user
returnAmount = stake + profit
```

Example:

```text
stake = 100
cashout profit = 30
returnAmount = 130
```

### CANCELLED

```text
profit = 0
returnAmount = stake
```

A cancelled bet should generally not affect performance metrics.

---

## 8. Idempotency

Consumers must be idempotent.

The Analytics Service should store or track processed `eventId` values to avoid duplicate processing.

Minimum recommendation:

Create a table:

```text
processed_events
```

Suggested fields:

```text
event_id
event_type
processed_at
```

Before processing an event, the Analytics Service should check whether the event was already processed.

If it was already processed, the event should be acknowledged and ignored.

---

## 9. Ordering

The first version does not guarantee strict global ordering.

However, the Analytics Service should handle normal lifecycle order:

```text
BET_CREATED
BET_UPDATED
BET_SETTLED
```

If a settlement event arrives before the creation event, the first version may either:

- create the projection if enough data exists in the event, or
- store the event as failed for manual retry.

Recommended first implementation:

- `BET_CREATED` creates projection.
- `BET_UPDATED` updates projection if it exists.
- `BET_SETTLED` updates projection if it exists.
- If projection does not exist, log the error and reject or dead-letter the event.

Future improvement:

- Implement retry and dead-letter handling.
- Include full bet snapshot in every event.
- Make consumers more tolerant to out-of-order messages.

---

## 10. Error Handling

If event processing fails, the Analytics Service should not silently ignore the event.

First version behavior:

- Log the error with `eventId`, `eventType`, and `betId`.
- Let the message be retried according to RabbitMQ/Spring configuration.
- Avoid infinite retry loops.

Future behavior:

- Configure a dead-letter exchange.
- Configure a dead-letter queue.
- Add manual reprocessing support.

Suggested future names:

```text
betting.events.dlx
analytics.betting-events.dlq
```

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

Rules:

- Non-breaking additions may keep the same version if consumers ignore unknown fields.
- Breaking changes must create a new version.
- Do not remove fields from existing event versions.
- Do not change the meaning of an existing field.
- Do not change field types without a new version.

Examples of breaking changes:

- Renaming `betId`.
- Changing `stake` from number to string.
- Removing `userId`.
- Changing `status` values.

---

## 12. Serialization

Events should be serialized as JSON.

Date/time fields should use ISO-8601 format.

Example:

```text
2026-07-21T22:00:00Z
```

Money fields should use decimal values.

Examples:

```text
100.00
2.10
110.00
```

In Java, use `BigDecimal` for monetary values and odds.

Avoid using `double` or `float` for money.

---

## 13. Security and Privacy

Events should include only the data needed by consumers.

Events must not include:

- User password
- User email, unless explicitly required
- JWT tokens
- Refresh tokens
- Payment data
- Sensitive personal information

The first version should identify users by:

```text
userId
```

---

## 14. Analytics Projection

The Analytics Service should maintain a local projection table.

Suggested table:

```text
analytics_bets
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

This table should be updated by consuming events.

Dashboard queries should read from this table, not from the Betting Service database.

---

## 15. Consumer Behavior by Event

### BET_CREATED

Action:

```text
Insert analytics_bets record.
```

If record already exists:

```text
Update existing record or ignore as duplicate, depending on eventId processing.
```

---

### BET_UPDATED

Action:

```text
Update editable fields in analytics_bets.
```

Editable fields:

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

Do not use this event for settlement fields.

---

### BET_SETTLED

Action:

```text
Update settlement fields in analytics_bets.
```

Settlement fields:

```text
status
profit
return_amount
settled_at
updated_at
```

---

## 16. Publisher Guidelines

The Betting Service should publish an event only after the related database transaction succeeds.

Recommended future improvement:

```text
Transactional Outbox Pattern
```

For the first version, direct publishing after save is acceptable for learning purposes.

Agents should not implement the outbox pattern unless explicitly requested.

---

## 17. Consumer Guidelines

The Analytics Service should:

- Consume events from `analytics.betting-events.queue`.
- Validate the envelope.
- Validate the payload.
- Check idempotency using `eventId`.
- Apply the projection change.
- Store the processed event.
- Log success or failure.

---

## 18. Local Development

RabbitMQ should be available through Docker Compose.

Suggested ports:

```text
5672:5672
15672:15672
```

Port `5672` is used by applications.

Port `15672` is used by RabbitMQ Management UI.

Suggested default local credentials:

```text
guest / guest
```

Do not use these credentials in production.

---

## 19. Initial RabbitMQ Constants

Suggested constants:

```text
Exchange: betting.events

Queue: analytics.betting-events.queue

Routing keys:
- bet.created
- bet.updated
- bet.settled

Event types:
- BET_CREATED
- BET_UPDATED
- BET_SETTLED
```

---

## 20. Future Improvements

Possible future improvements:

- Dead-letter queue.
- Retry configuration.
- Transactional outbox.
- Event schema validation.
- Contract tests for events.
- Full bet snapshot in all events.
- Event replay.
- Audit log.
- Event correction flow.
- Separate queues per event type.
- Multiple consumers for notification or recommendation services.

These improvements are out of scope for the first implementation.