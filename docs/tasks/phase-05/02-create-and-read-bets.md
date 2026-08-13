# 5.2 — Create and retrieve a user's bets

## Context

The Betting Service owns the Bet lifecycle and persistence.

Task 5.1 establishes the pure Bet domain model, value objects, financial precision, and initial `PENDING` state.

The API Gateway validates the authenticated user's JWT and propagates the authenticated identity internally using:

```text
X-User-Id
```

The Betting Service must use this authenticated identity as the Bet owner.

The client must never choose or override `userId`.

This task introduces the minimum persistence required for Bets and exposes the documented creation and retrieval contracts.

## Objective

Persist newly created PENDING bets and allow authenticated users to retrieve only their own bets.

Implement the documented contracts for:

```text
POST /bets
GET /bets
GET /bets/{id}
```

## Ownership contract

Every Bet belongs to exactly one authenticated user.

The Betting Service must obtain ownership exclusively from:

```text
X-User-Id
```

The API must not trust `userId` received through:

* request body;
* query parameters;
* path parameters;
* arbitrary client headers other than the trusted Gateway identity context.

If a request body contains a `userId`, it must not override the authenticated identity.

A Bet persisted by the service must always contain the UUID derived from the authenticated identity context.

## Authenticated identity

For protected Betting Service requests:

```http
X-User-Id: <authenticated-user-uuid>
```

The value must:

* be present;
* be a valid UUID.

Missing or malformed authenticated identity returns:

```text
401 Unauthorized
```

The Betting Service does not revalidate the original Bearer JWT.

JWT validation remains the responsibility of the API Gateway.

## Bet creation

A valid:

```text
POST /bets
```

returns:

```text
201 Created
```

The request and response fields must follow the exact contract documented in `docs/api-contracts.md`.

Creation must use the domain objects established in Task 5.1 for stake and odds.

A newly created Bet must be persisted with:

```text
status = PENDING
profit = null
returnAmount = null
settledAt = null
```

The service, not the client, determines:

```text
id
userId
status
profit
returnAmount
settledAt
createdAt
updatedAt
```

The client must not be able to create an already settled Bet.

### Domain validation

Invalid stake or odds must be rejected using the domain rules from Task 5.1.

Domain invariants must not be duplicated with different behavior in the persistence or controller layers.

Invalid creation input returns the documented validation/domain error contract.

## Persistence

This task owns the minimum Betting Service persistence required to store and retrieve Bets.

Create the required database migration/schema if it does not already exist.

Persistence must support the domain fields required by the current MVP, including:

```text
id
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
profit
returnAmount
placedAt
settledAt
notes
createdAt
updatedAt
```

Persistence representation must preserve the decimal precision established in Task 5.1.

The persistence layer must not use `double` or `float` for financial values.

Domain objects must not depend on JPA or persistence annotations.

## List bets

```text
GET /bets
```

returns only Bets belonging to the authenticated user.

The endpoint must never return Bets owned by another user.

An empty result returns:

```text
200 OK
```

with the documented empty collection/page representation.

The listing endpoint must support the optional filters and pagination documented in `docs/api-contracts.md`.

Where applicable to the documented contract, Bet-listing date filtering uses:

```text
placedAt
```

Filters must always be combined with authenticated ownership.

Supplying filters must never allow the user isolation predicate to be bypassed.

If pagination, sorting, default page size, maximum page size, or filter semantics are not explicitly documented in `docs/api-contracts.md`, do not invent them; report the contractual gap before creating exact tests.

## Retrieve Bet by ID

```text
GET /bets/{id}
```

returns a Bet only when:

* the Bet exists;
* the Bet belongs to the authenticated user.

A missing Bet returns:

```text
404 Not Found
```

Attempting to read another user's Bet must return the same external behavior:

```text
404 Not Found
```

The API must not reveal whether the requested Bet:

* does not exist;
* exists but belongs to another user.

Cross-user lookup and nonexistent Bet lookup must therefore remain externally indistinguishable.

## Response safety

Bet responses must follow the documented API contract.

Responses must not expose:

* internal persistence entities;
* credentials;
* JWTs;
* Authorization headers;
* internal identity headers;
* stack traces;
* database details;
* internal implementation details.

## Acceptance criteria

* [ ] Valid creation returns `201 Created`.
* [ ] A created Bet is persisted.
* [ ] A created Bet starts as `PENDING`.
* [ ] A created Bet has `profit = null`.
* [ ] A created Bet has `returnAmount = null`.
* [ ] A created Bet has `settledAt = null`.
* [ ] Authenticated identity determines `userId`.
* [ ] Request input cannot override `userId`.
* [ ] Invalid stake is rejected according to Task 5.1.
* [ ] Invalid odds are rejected according to Task 5.1.
* [ ] Monetary persistence preserves the Task 5.1 precision contract.
* [ ] Listing returns only the authenticated user's Bets.
* [ ] Listing with no results returns the documented empty result.
* [ ] Documented filters preserve user isolation.
* [ ] Documented pagination preserves user isolation.
* [ ] Reading an owned Bet returns the documented response.
* [ ] Missing Bet returns `404`.
* [ ] Another user's Bet returns `404`.
* [ ] Missing and cross-user Bets are externally indistinguishable.
* [ ] Missing `X-User-Id` returns `401`.
* [ ] Malformed `X-User-Id` returns `401`.

## Boundary and negative cases

### Creation

* valid Bet;
* zero stake;
* negative stake;
* odds equal to one;
* odds below one;
* stake requiring normalization;
* odds requiring normalization;
* client-supplied `userId`;
* client-supplied lifecycle/financial fields if the API contract allows arbitrary JSON fields.

### Ownership

* authenticated user creates a Bet;
* authenticated user retrieves own Bet;
* authenticated user cannot retrieve another user's Bet;
* query/filter input cannot bypass ownership;
* missing authenticated identity;
* malformed authenticated identity.

### Listing

* no Bets;
* one Bet;
* multiple Bets belonging to the same user;
* Bets belonging to multiple users with only the authenticated user's Bets returned;
* each documented optional filter;
* documented pagination boundaries.

### Retrieval

* existing owned ID;
* nonexistent ID;
* ID owned by another user;
* malformed ID according to the documented API error contract.

## Out of scope

* Updating Bets.
* Settlement.
* Profit recalculation outside creation's PENDING state.
* RabbitMQ event publishing.
* Analytics aggregation.
* Settlement correction.
* Deletion.
* Bulk Bet creation.
* External sportsbook validation.

## Dependencies

* Task 5.1.
* Gateway authenticated identity contract.
* PostgreSQL infrastructure from Phase 1.

## Expected tests

### Domain/application tests

Cover:

* creation orchestration;
* authenticated ownership;
* invalid domain values;
* listing isolation;
* retrieval isolation;
* missing/cross-user behavior.

### Repository integration tests

Use the project's documented persistence integration strategy to validate:

* Bet persistence;
* retrieval by ID and owner;
* listing by owner;
* user isolation;
* decimal persistence;
* filtering/pagination where implemented;
* database migration/schema.

### API integration tests

Cover:

* `POST /bets`;
* `GET /bets`;
* `GET /bets/{id}`;
* identity handling;
* validation;
* empty results;
* cross-user isolation;
* documented filters and pagination;
* safe error responses.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
