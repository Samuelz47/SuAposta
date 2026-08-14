# 5.3 — Update and settle a pending bet

## Context

The Betting Service owns the Bet lifecycle.

Task 5.1 defines:

* financial value objects;
* decimal precision;
* settlement calculations;
* valid settlement statuses;
* lifecycle transitions.

Task 5.2 establishes persistence, authenticated ownership, creation, and retrieval.

Only an authenticated owner may modify or settle a Bet.

Only a `PENDING` Bet may be updated or settled.

## Objective

Expose the documented API behavior for:

* updating an owned PENDING Bet;
* settling an owned PENDING Bet into one of the supported final statuses.

Settlement must delegate financial calculations and lifecycle enforcement to the domain behavior established in Task 5.1.

## Ownership contract

Ownership is derived exclusively from the trusted authenticated identity:

```text
X-User-Id
```

The client must not choose or override Bet ownership.

Every update or settlement lookup must be constrained by both:

```text
betId
authenticated userId
```

A Bet belonging to another user must not be exposed.

Cross-user operations must return the same external result as operations against a nonexistent Bet:

```text
404 Not Found
```

## Update contract

Use the exact update endpoint and request/response shape documented in `docs/api-contracts.md`.

Only a Bet whose current status is:

```text
PENDING
```

may be updated.

Editable Bet information for the initial MVP may include the documented mutable betting information such as:

```text
sport
league
homeTeam
awayTeam
market
selection
odds
stake
placedAt
notes
```

The exact accepted fields must follow `docs/api-contracts.md`.

The update contract must never permit the client to directly modify:

```text
id
userId
status
profit
returnAmount
settledAt
createdAt
```

`updatedAt` is controlled by the service.

If the exact mutable field set differs in `docs/api-contracts.md`, the API contract is authoritative unless it conflicts with the domain lifecycle.

Updating stake or odds must reapply the Task 5.1 domain invariants and decimal normalization.

## Updating settled Bets

Attempts to update a Bet whose status is any final status:

```text
WON
LOST
VOID
CASHOUT
CANCELLED
```

must be rejected with:

```text
409 Conflict
```

The failed operation must not partially modify the Bet.

Its existing:

```text
status
profit
returnAmount
settledAt
```

must remain unchanged.

## Settlement contract

Use the exact settlement endpoint and request/response contract documented in `docs/api-contracts.md`.

Only a `PENDING` Bet may be settled.

Supported final settlement statuses are:

```text
WON
LOST
VOID
CASHOUT
CANCELLED
```

The client must not directly inform:

```text
profit
```

The domain calculates financial results.

## WON

Settlement to:

```text
WON
```

calculates:

```text
returnAmount = stake × odds
profit = returnAmount − stake
```

## LOST

Settlement to:

```text
LOST
```

calculates:

```text
returnAmount = 0.00
profit = -stake
```

## VOID

Settlement to:

```text
VOID
```

calculates:

```text
returnAmount = stake
profit = 0.00
```

## CANCELLED

Settlement to:

```text
CANCELLED
```

calculates:

```text
returnAmount = stake
profit = 0.00
```

## CASHOUT

Settlement to:

```text
CASHOUT
```

requires an explicit cashout return amount.

The domain calculates:

```text
returnAmount = cashoutReturn
profit = cashoutReturn − stake
```

CASHOUT may result in:

* positive profit;
* zero profit;
* negative profit.

Missing required CASHOUT return amount is an invalid request and returns:

```text
400 Bad Request
```

The user must never directly submit calculated `profit`.

For settlement statuses other than `CASHOUT`, financial return values must be derived entirely by the domain and must not depend on a client-provided return amount.

## Settlement timestamps

A successful settlement must assign:

```text
settledAt
```

at settlement time.

`settledAt` must not be supplied or overridden by the client.

`updatedAt` must reflect the successful modification.

A failed settlement must not change `settledAt`.

## Repeated settlement

A Bet already in any final status cannot be settled again.

Repeated settlement returns:

```text
409 Conflict
```

This includes any transition from a final state to:

* the same final state;
* another final state.

Examples:

```text
WON -> WON
WON -> LOST
LOST -> WON
VOID -> WON
CASHOUT -> WON
CANCELLED -> WON
```

A rejected repeated settlement must preserve:

```text
status
profit
returnAmount
settledAt
```

No partial state mutation is allowed.

## Missing and cross-user Bets

For both update and settlement:

### Bet does not exist

Return:

```text
404 Not Found
```

### Bet belongs to another user

Return:

```text
404 Not Found
```

The two scenarios must remain externally indistinguishable.

The API must not reveal whether another user's Bet exists.

## Invalid identity

Missing authenticated identity returns:

```text
401 Unauthorized
```

Malformed/non-UUID authenticated identity returns:

```text
401 Unauthorized
```

## Error behavior

Use the documented safe API error contract.

Use:

```text
400 Bad Request
```

for structurally invalid settlement/update input or domain input validation where documented.

Use:

```text
401 Unauthorized
```

for missing or malformed authenticated identity.

Use:

```text
404 Not Found
```

for nonexistent or cross-user Bets.

Use:

```text
409 Conflict
```

for lifecycle conflicts such as:

* updating an already settled Bet;
* settling an already settled Bet.

Errors must not expose:

* another user's ownership;
* persistence details;
* internal entities;
* JWTs;
* internal identity headers;
* stack traces;
* implementation details.

## Acceptance criteria

* [ ] Only the authenticated owner can update a Bet.
* [ ] Only a `PENDING` Bet can be updated.
* [ ] Updating an already settled Bet returns `409`.
* [ ] Update reuses Stake/Odds domain validation from Task 5.1.
* [ ] Update cannot change ownership.
* [ ] Update cannot directly change settlement state or calculated financial fields.
* [ ] Only the authenticated owner can settle a Bet.
* [ ] Only a `PENDING` Bet can settle.
* [ ] PENDING may settle to `WON`.
* [ ] PENDING may settle to `LOST`.
* [ ] PENDING may settle to `VOID`.
* [ ] PENDING may settle to `CASHOUT`.
* [ ] PENDING may settle to `CANCELLED`.
* [ ] WON calculation matches Task 5.1.
* [ ] LOST calculation matches Task 5.1.
* [ ] VOID calculation matches Task 5.1.
* [ ] CANCELLED calculation matches Task 5.1.
* [ ] CASHOUT calculation matches Task 5.1.
* [ ] CASHOUT requires the documented return amount.
* [ ] Client does not directly determine profit.
* [ ] Successful settlement assigns `settledAt`.
* [ ] Repeated settlement returns `409`.
* [ ] Failed lifecycle operations do not partially mutate persisted state.
* [ ] Nonexistent Bet returns `404`.
* [ ] Cross-user operation returns `404`.
* [ ] Nonexistent and cross-user Bets are externally indistinguishable.
* [ ] Missing identity returns `401`.
* [ ] Malformed identity returns `401`.

## Boundary and negative cases

### Update

* owner updates valid PENDING Bet;
* stake update;
* odds update;
* invalid stake;
* invalid odds;
* update attempt against each final status;
* client attempts to modify `userId`;
* client attempts to modify `status`;
* client attempts to modify `profit`;
* client attempts to modify `returnAmount`;
* missing Bet;
* cross-user Bet.

### Settlement

* `PENDING -> WON`;
* `PENDING -> LOST`;
* `PENDING -> VOID`;
* `PENDING -> CANCELLED`;
* `PENDING -> CASHOUT` with positive result;
* `PENDING -> CASHOUT` with zero result;
* `PENDING -> CASHOUT` with negative result;
* CASHOUT without required return amount;
* repeated settlement for every final status;
* transition from one final status to another;
* missing Bet;
* cross-user Bet.

### State integrity

After rejected update or settlement, verify that persisted values remain unchanged.

## Out of scope

* Settlement correction.
* Reopening settled Bets.
* Bet deletion.
* RabbitMQ event publishing.
* Analytics projection updates.
* Bulk updates.
* External sportsbook synchronization.
* Administrative override.

## Dependencies

* Task 5.1.
* Task 5.2.
* Gateway authenticated identity contract.

## Expected tests

### Domain tests

Reuse and preserve Task 5.1 settlement and lifecycle coverage.

Do not duplicate domain rules in higher layers with different behavior.

### Application tests

Cover:

* ownership orchestration;
* update of PENDING Bet;
* update lifecycle conflicts;
* settlement orchestration;
* cross-user isolation;
* missing Bet behavior;
* persistence of domain-calculated settlement results.

### Persistence integration tests

Cover:

* update persistence;
* settlement persistence;
* decimal precision;
* `settledAt` persistence;
* preservation of state after rejected operations.

### API integration tests

Cover:

* documented update endpoint;
* documented settlement endpoint;
* owner success;
* validation errors;
* lifecycle conflicts;
* every final settlement status;
* CASHOUT boundaries;
* missing Bet;
* cross-user behavior;
* safe error responses.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Current status: `DONE`

| Current status | Pending gate |
| --- | --- |
| DONE | Independent final QA verdict approved by human. |

| Gate | Decision / evidence | Date / approver |
| --- | --- | --- |
| Specification provided | Approved Task 5.3 contract and settledAt decision reviewed | 2026-08-14 / Human |
| Tests in Red | `./gradlew :services:betting-service:compileTestJava --no-daemon` failed only because the Task 5.3 implementation types are not yet present: `UpdateBetService`, `SettleBetService`, `UpdateBetCommand`, and `SettleBetCommand`. Production compilation passed with `./gradlew :services:betting-service:compileJava --no-daemon`. | 2026-08-14 / Test agent |
| Tests approved | Human approved the blind tests and authorized handoff to the implementation agent. | 2026-08-14 / Human |
| Implementation in Green | Implemented ownership-scoped update and settlement use cases, atomic pending-Bet update, domain-delegated settlement, service-controlled timestamps through `Clock`, HTTP endpoints, safe lifecycle conflict handling, and the documented GET pagination defaults. Focused Task 5.3 results: application 14/14, API 36/36, persistence 4/4. Betting Service 168/168. `:services:betting-service:check`, `:services:api-gateway:check`, two independent `./gradlew check --rerun-tasks` executions, and `git diff --check` passed. No migration or event publishing was added. | 2026-08-14 / implementation agent |
| Human diff review | Human explicitly approved the implementation diff and authorized handoff to the final QA agent. | 2026-08-14 / human |
| QA verdict | `APPROVED WITH RESERVATIONS`. Human approved the QA outcome. The remaining API-contract markdown fence and test-strengthening follow-ups are non-blocking. | 2026-08-14 / Human |

### Test-agent handoff

The blind-test, implementation, human diff review, and independent QA stages are complete. The human approved the QA outcome and finalized this task. No production code was changed by the test agent.

### Approved-test changes

No approved tests from Tasks 5.1 or 5.2 were altered.

On 2026-08-14, the human explicitly authorized three corrections to the protected Task 5.3 tests after the first implementation run exposed test-only defects:

* added the missing `finalStatuses` method source used by the final-Bet update conflict test, without changing its expectations;
* corrected only the rounded CASHOUT return expectation from `80.126` to `80.13`, preserving the approved scale `2`, `HALF_UP`, and `profit = -19.87` contract;
* corrected `BetTask53HttpTestSupport` so a missing `X-User-Id` sends an HTTP request without that header instead of failing inside the Java HTTP client.

No other expectation, fixture, test, or Task 5.1/5.2 coverage was changed.
