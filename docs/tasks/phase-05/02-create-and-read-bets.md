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

This task introduces the minimum application and persistence behavior required for Bets and exposes the documented creation and retrieval contracts.

## Objective

Persist newly created PENDING bets and allow authenticated users to retrieve only their own bets.

Implement the documented contracts for:

```text
POST /bets
GET /bets
GET /bets/{id}
```

The implementation must preserve the architecture boundaries:

```text
presentation
    ↓
application
    ↓
domain
    ↓
persistence port
    ↓
infrastructure
```

Controllers remain thin.

Business invariants remain in the domain.

Application services coordinate use cases and ownership.

Infrastructure implements persistence.

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

Ownership enforcement must not depend solely on controllers or response filtering.

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

## Application layer contract

Task 5.2 introduces the minimum application use cases required for Bet creation and retrieval.

The application layer must expose explicit use cases for:

* creating a Bet;
* listing Bets;
* retrieving a Bet by ID.

The application layer owns orchestration only.

It must not duplicate Task 5.1 domain rules.

### Create Bet use case

Responsible for:

* receiving the authenticated `userId`;
* receiving the documented Bet creation input;
* creating the domain Bet using Task 5.1 value objects;
* assigning ownership exclusively from the authenticated identity;
* persisting the new Bet;
* returning the created Bet representation required by the presentation layer.

Recommended application service name:

```text
CreateBetService
```

Equivalent naming is allowed if the same responsibility and architectural boundary are preserved.

### List Bets use case

Responsible for:

* receiving the authenticated `userId`;
* receiving the documented filters;
* receiving the documented pagination;
* querying Bets already constrained to the authenticated owner;
* returning the list/page representation required by the presentation layer.

Recommended application service name:

```text
ListBetsService
```

Equivalent naming is allowed.

### Get Bet use case

Responsible for:

* receiving the authenticated `userId`;
* receiving the requested Bet ID;
* retrieving the Bet using an ownership-constrained persistence lookup;
* returning the Bet only when it belongs to the authenticated user;
* treating nonexistent and cross-user Bets using the same application-level not-found behavior.

Recommended application service name:

```text
GetBetService
```

Equivalent naming is allowed.

### Application layer constraints

Application services must not:

* perform HTTP concerns;
* access JPA repositories directly unless they are the declared persistence port implementation;
* duplicate Stake/Odds validation;
* load unrestricted Bet collections and filter ownership in memory;
* expose persistence entities;
* decide ownership from request payload data.

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

Domain invariants must not be duplicated with different behavior in the application, persistence, or controller layers.

Invalid creation input returns the documented validation/domain error contract.

## Repository port contract

The application layer depends on a Betting persistence port responsible for Bet storage and ownership-constrained queries.

Recommended port name:

```text
BetRepository
```

Equivalent naming is allowed if the same semantics are preserved.

The repository contract must support, at minimum, behavior equivalent to:

```text
save(Bet)
findByIdAndUserId(betId, userId)
findAllByUserId(userId, filters, pagination)
```

Exact Java method signatures, parameter objects, return types, or method names may vary.

The behavioral contract is authoritative.

### Save

Persistence must be able to save a Bet while preserving:

* Bet ID;
* owner `userId`;
* domain state;
* decimal precision;
* timestamps;
* nullable financial fields.

### Ownership-constrained lookup

Retrieval by ID must be constrained by both:

```text
betId
userId
```

The application must not retrieve an unrestricted Bet and expose whether it belongs to another user.

A lookup for another user's Bet must behave as if no Bet were found.

### Ownership-constrained listing

Listing queries must always contain authenticated ownership as part of their persistence/query boundary.

The implementation must not:

* load all Bets and filter them in the controller;
* load all Bets and filter ownership in the application layer;
* expose another user's Bet before applying ownership;
* allow filters to replace or bypass the `userId` predicate.

Filters and pagination must be applied together with ownership.

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

The infrastructure layer may use JPA entities or equivalent persistence models, but these must remain separate from the domain model.

## Persistence integration contract

Repository integration tests must directly validate the concrete persistence adapter and database schema.

They must cover at least:

* save and reload a Bet;
* preservation of Bet ID;
* preservation of `userId`;
* preservation of `PENDING`;
* Stake scale `2` after persistence;
* Odds scale `4` after persistence;
* `profit = null` for pending Bets;
* `returnAmount = null` for pending Bets;
* `settledAt = null` for pending Bets;
* ownership-constrained retrieval;
* cross-user isolation;
* listing by authenticated user;
* documented filters at query/persistence level;
* documented pagination at query/persistence level;
* migration/schema initialization.

Repository integration tests must exercise the concrete persistence implementation rather than proving persistence only indirectly through HTTP.

They must not depend on pre-existing database rows.

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

The ownership restriction should be enforced as part of the lookup itself rather than retrieving another user's Bet and rejecting it only in the presentation layer.

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
* [ ] Creation orchestration lives in the application layer.
* [ ] Listing orchestration lives in the application layer.
* [ ] Retrieval orchestration lives in the application layer.
* [ ] Persistence is accessed through an explicit port.
* [ ] Retrieval is constrained by `betId + userId` or equivalent ownership-safe semantics.
* [ ] Listing is constrained by authenticated `userId` at query/persistence level.
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
* [ ] Domain objects remain free from persistence framework concerns.

## Boundary and negative cases

### Creation

* valid Bet;
* zero stake;
* negative stake;
* stake that normalizes to zero;
* odds equal to one;
* odds below one;
* odds that normalize to one;
* stake requiring normalization;
* odds requiring normalization;
* client-supplied `userId`;
* client-supplied lifecycle/financial fields if the API contract allows arbitrary JSON fields.

### Ownership

* authenticated user creates a Bet;
* persisted Bet belongs to authenticated user;
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

### Persistence

* save/reload round trip;
* application restart or equivalent persistence proof;
* direct adapter/repository retrieval;
* cross-user repository lookup;
* decimal round trip;
* null pending financial fields;
* migration on clean database.

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

### Application unit tests

Application tests must be pure unit tests.

They must not require:

* Spring Context;
* HTTP;
* PostgreSQL;
* JPA;
* Testcontainers.

Use mocks or fakes for persistence ports according to `docs/testing-strategy.md`.

#### Create Bet

Cover:

* authenticated `userId` becomes the persisted owner;
* valid creation invokes persistence;
* persisted Bet starts as `PENDING`;
* `profit`, `returnAmount`, and `settledAt` are null;
* ownership does not come from request payload;
* application orchestration delegates Stake/Odds invariants to the Task 5.1 domain.

#### List Bets

Cover:

* authenticated `userId` is part of the repository query;
* documented filters are forwarded;
* documented pagination is forwarded;
* no unrestricted global Bet lookup is used;
* returned result corresponds only to the authenticated user query.

#### Get Bet

Cover:

* lookup uses both Bet ID and authenticated user ID;
* owned Bet is returned;
* missing Bet produces application not-found behavior;
* cross-user Bet is indistinguishable from missing because the ownership-constrained repository lookup returns no Bet.

Tests should verify behavior and boundaries, not unnecessary implementation details.

Recommended service names may be used by blind tests where concrete types are required.

## Repository integration tests

Use the project's documented persistence integration strategy.

Cover:

* migration/schema creation;
* Bet persistence;
* save/reload;
* retrieval by ID and owner;
* cross-user lookup returning no Bet;
* listing by owner;
* user isolation;
* decimal persistence;
* PENDING/null financial state;
* documented filters at persistence level;
* documented pagination at persistence level.

These tests should exercise the concrete persistence adapter and database.

## API integration tests

Cover:

* `POST /bets`;
* `GET /bets`;
* `GET /bets/{id}`;
* identity handling;
* validation;
* ownership on creation;
* empty results;
* multiple Bets;
* cross-user isolation;
* documented filters;
* documented pagination;
* owned retrieval;
* missing retrieval;
* cross-user retrieval;
* safe error responses.

## Test data isolation

Test data must be isolated and repeatable.

Use unique identifiers where necessary.

Tests must not depend on:

* pre-existing database rows;
* execution order;
* global shared fixtures;
* another test's side effects.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Current status:

```text
DONE
```

The original Task 5.2 blind API/persistence tests remain valid.

The specification was subsequently clarified to define:

* application-layer use cases;
* repository port semantics;
* direct persistence integration requirements.

Additional blind coverage for those clarified contracts has been added in Red and approved by the human.

| Current status | Pending gate |
| -------------- | ------------ |
| DONE           | —            |

| Gate                                    | Decision / evidence                                                                                                                                                                                           | Date / approver                    |
| --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------- |
| Specification provided                  | Original API/ownership contract reviewed. Application and repository contracts were later clarified before implementation.                                                                                    | 2026-08-13 / human + test workflow |
| Initial tests in Red                    | `BetApiIntegrationTest` and `BetPersistenceIntegrationTest` produced 34 intentional Reds because `POST /bets`, `GET /bets`, and `GET /bets/{id}` are not implemented. Existing Task 5.1 tests remained Green. | 2026-08-13 / test agent            |
| Additional application/repository tests | `BetApplicationServicesTest` specifies 13 pure-unit invocations and `BetRepositoryIntegrationTest` specifies 15 direct-persistence invocations. `compileTestJava` reports 52 intentional compilation errors because the application services/DTOs, `BetRepository` port, persistence adapter/schema, and persistent Bet identity fields do not exist yet. Existing Task 5.2 tests remain at 34 expected Reds; the 49 approved Task 5.1 tests remain Green. Commands: `./gradlew :services:betting-service:compileTestJava --no-daemon`; focused `test --tests` commands for each new class; focused existing Task 5.2 and Task 5.1 commands; `./gradlew :services:betting-service:test --no-daemon`. | 2026-08-13 / test agent |
| Tests approved                          | Human approved the complete Task 5.2 Red suite, including the additional application and direct repository integration coverage.                                                                              | 2026-08-13 / human                 |
| Implementation in Green                 | Implemented the application use cases, ownership-scoped repository port/adapter, PostgreSQL migration, and `POST /bets`, `GET /bets`, and `GET /bets/{id}` contracts. Task 5.1: 49/49 Green. Task 5.2: 62/62 Green. Betting Service: 114/114 Green. The Gateway test doubles were changed to ephemeral ports with test-only upstream URL overrides so the root check is deterministic. `:services:betting-service:test --rerun-tasks`, `:services:betting-service:check --rerun-tasks`, `:services:api-gateway:test --rerun-tasks`, `:services:api-gateway:check --rerun-tasks`, and two independent `./gradlew check --rerun-tasks` executions all passed; each root execution reported 206/206 tests Green. `git diff --check` passed. | 2026-08-13 / implementation agent |
| Human diff review                       | Human explicitly approved the implementation and authorized the pre-QA handoff.                                                                                                                                 | 2026-08-13 / human                 |
| QA verdict                              | `APPROVED WITH RESERVATIONS`. Human approved the QA outcome; the task is finalized. The remaining API-contract ambiguities are recorded as non-blocking follow-ups.                                                                                                  | 2026-08-13 / human                 |

### Existing test scope and evidence

The existing Red suite already covers:

* valid creation;
* exact pending response;
* ownership through `X-User-Id`;
* missing/malformed identity;
* Stake and Odds boundaries;
* decimal normalization;
* safe errors;
* empty listing;
* multiple-Bet listing;
* cross-user isolation;
* documented listing filters;
* explicit page/size behavior;
* owned retrieval;
* missing retrieval;
* cross-user retrieval;
* external error indistinguishability;
* persistence across application context restart.

These tests must not be removed or weakened.

### Contractual gaps intentionally not invented

The following remain governed by `docs/api-contracts.md`:

* pagination defaults;
* maximum page size;
* sort syntax;
* default ordering;
* malformed Bet-ID status/error behavior;
* unknown JSON-field handling.

If these are not explicitly documented, tests must not invent exact behavior.

### Approved-test changes

Task 5.1 tests and implementation must remain unchanged.

The existing Task 5.2 tests must remain intact while the missing application and direct repository coverage is added.

On 2026-08-13, the human explicitly approved two corrections to the protected Task 5.2 API tests after review established that the expectations, rather than production behavior, were incorrect:

* API decimal assertions compare the exact `BigDecimal` value without requiring a JSON scale. Structural precision remains covered by the Task 5.1 domain tests and direct repository integration tests (`Stake` scale 2 and `Odds` scale 4). This corrects the API representation expectation without changing field, numeric-type, normalization, domain, or persistence coverage.
* With the documented filter fixtures (`FIRST.stake = 100.00`, `SECOND.stake = 50.00`), `minStake=75.00` expects FIRST and `maxStake=75.00` expects SECOND. This corrects only the inverted fixture mapping and preserves ownership-scoped filter coverage.

Affected acceptance criteria: monetary precision preservation, documented filter behavior, and documented pagination/filter ownership isolation. No application, repository, Task 5.1, or production expectation was changed.
