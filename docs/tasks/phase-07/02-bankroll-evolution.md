# 7.2 — Expose bankroll evolution

## Context

Task 7.1 establishes the first Analytics read-side use case and dashboard summary metrics over `analytics_bets`.

Task 7.2 adds a chronological performance view derived from the same Analytics-owned projection.

Bankroll evolution in this MVP is not a separate financial ledger. It is a cumulative analytical series based on settled betting performance ordered by the documented settlement timestamp.

The implementation must follow the existing rules in:

- `docs/domain.md`
- `docs/api-contracts.md`
- `docs/architecture.md`

Those documents remain the source of truth for:

- eligible statuses;
- timestamp semantics;
- filter behavior;
- decimal precision;
- API representation;
- authenticated-user isolation.

This task must not invent deposits, withdrawals, bankroll accounts, or balance management semantics.

## Objective

Expose the authenticated user's filtered bankroll evolution as an ordered sequence of cumulative performance points derived exclusively from Analytics-owned projections.

The returned series must:

- use only documented eligible settled bets;
- respect authenticated-user isolation;
- apply documented filters;
- be ordered deterministically;
- calculate cumulative profit correctly;
- represent zero-based bankroll behavior exactly as documented.

## Scope

This task includes:

- application-level bankroll evolution calculation;
- Analytics projection querying;
- authenticated-user isolation;
- documented filters;
- settlement-status eligibility;
- deterministic chronological ordering;
- cumulative profit calculation;
- zero-based bankroll representation when applicable;
- Analytics API boundary for bankroll evolution;
- application/domain calculation tests;
- persistence integration tests;
- API integration tests.

This task does not introduce new event-processing behavior.

## Dependencies

Required completed tasks:

- Task 6.3 — idempotent Analytics projections;
- Task 7.1 — dashboard summary metrics.

Task 6.3 and Task 7.1 must remain behaviorally unchanged.

## Source of truth

Use `docs/domain.md` for bankroll evolution semantics.

Use `docs/api-contracts.md` for request/response behavior.

If the documents are insufficient to determine any of the following:

- eligible statuses;
- timestamp used for ordering/filtering;
- initial bankroll behavior;
- cumulative calculation formula;
- point structure;
- ordering tie-break behavior;
- decimal scale;
- rounding;
- filter semantics;

the implementation agent must stop and report the ambiguity.

Do not create a new bankroll rule inside this task.

## Architecture boundary

The expected direction is:

```text
Analytics API
    ↓
application use case
    ↓
application persistence port
    ↓
Analytics persistence adapter
    ↓
analytics_bets
```

The controller/API boundary must remain thin.

It may:

- identify the authenticated user;
- validate request filters;
- invoke the bankroll evolution use case;
- map the result to the documented API response.

It must not own:

- SQL;
- cumulative calculations;
- status eligibility;
- ordering rules;
- persistence decisions.

The application layer owns bankroll evolution behavior.

Persistence infrastructure owns projection retrieval.

## Service isolation

Analytics must calculate bankroll evolution exclusively from its own projection database.

This task must not:

- access the Betting database;
- call Betting repositories;
- call Betting application services;
- synchronously request bet history from Betting Service;
- use RabbitMQ at read time.

The source of truth for this read model is `analytics_bets`.

## Authenticated user

Every bankroll evolution query belongs exclusively to the authenticated user.

User identity must come from the trusted authentication boundary already established by the system.

Client-controlled input must not select another user's data.

Every persistence query must include authenticated `userId` ownership scoping.

Cross-user projections must never affect:

- returned points;
- cumulative values;
- ordering;
- filtering.

## Eligible bets

Bankroll evolution uses only settled performance projections with these statuses:

```text
WON
LOST
CASHOUT
```

The following statuses are excluded:

```text
PENDING
VOID
CANCELLED
```

Excluded statuses must not create points and must not affect cumulative values. This is the explicit Task 7.2 eligibility contract; do not infer it from another Task 7.1 metric.

Each eligible bet generates exactly one point. Do not group points by date, time, sport, league, market, or any other period or dimension. Two eligible bets with the same settlement timestamp still generate two points.

## Pending bets

`PENDING` bets must not create bankroll evolution points.

A pending projection has no realized performance and must not accidentally affect cumulative profit.

## VOID and CANCELLED

`VOID` and `CANCELLED` are excluded from bankroll evolution. They must not create zero-value points and must not affect cumulative profit.

## CASHOUT

For `CASHOUT`, use the projected settlement values consumed in Task 6.3.

Do not recalculate:

- `profit`;
- `returnAmount`.

Bankroll evolution uses the persisted analytical projection.

## Profit source

The cumulative series must use the documented projected profit value.

Do not derive profit again from:

```text
stake
odds
status
returnAmount
```

Task 6.3 already projected the authoritative settlement values.

Task 7.2 aggregates those values.

## Chronological ordering

Points must be ordered by:

```text
settledAt ascending, then betId ascending
```

`settledAt` is the chronological settlement timestamp. The `betId` tie-breaker is mandatory when settlement timestamps are equal.

Do not order primarily by:

- `placedAt`;
- `createdAt`;
- `updatedAt`;

## Same-date / same-timestamp bets

Multiple eligible bets may share the same settlement date or timestamp. Each still produces one point. Equal `settledAt` values are ordered by `betId` ascending, so the result is deterministic and does not depend on incidental database row order.

## Cumulative calculation

For eligible bets ordered according to the contract, calculate cumulative profit sequentially.

The internal baseline is `0.00`. For the filtered eligible bets in documented order:

```text
cumulativeProfit = 0.00

for each eligible projection:
    cumulativeProfit = cumulativeProfit + projection.profit
    point.profit = projection.profit
    point.cumulativeProfit = cumulativeProfit
    point.bankroll = cumulativeProfit
```

Filtering happens before this calculation, so excluded projections never contribute to the first or later cumulative values. Do not reset cumulative values between points.

## Zero-based initial bankroll

This version has no persisted initial bankroll. The internal baseline is `0.00`; it exists only for the calculation and must not be returned as an artificial point.

The public `bankroll` field is equal to `cumulativeProfit`. In Task 7.2 it means cumulative performance from the zero baseline, not a real account balance.

## Negative bankroll values

Negative cumulative values are valid.

Examples:

```text
0.00
-10.00
-30.00
5.00
```

Do not clamp bankroll/cumulative profit at zero.

This task models performance evolution, not account solvency constraints.

## Zero-profit events

An eligible settled bet may have zero profit. It must still produce its point, even when the cumulative value does not change.

## Financial precision

Use `BigDecimal` for:

- profit;
- cumulative profit;
- bankroll value;
- any decimal response value.

Do not use:

- `double`;
- `float`.

This includes intermediate accumulation.

Do not convert cumulative values through floating-point representations.

## Scale and rounding

Apply scale and rounding exactly as documented.

Do not round every intermediate cumulative addition unless the domain contract requires it.

Prefer preserving authoritative projected monetary scale through accumulation and applying response normalization at the documented boundary.

Do not invent a new rounding mode.

## Empty data

If the authenticated user has no eligible settled projections, return the documented successful empty result.

Do not fail because no rows exist.

The successful response contains an empty `points` array.

Do not invent a synthetic settlement.

## Filters

Support every bankroll-evolution filter documented in `docs/api-contracts.md`.

Filters must apply before cumulative calculation.

Conceptually:

```text
authenticated user's analytics projections
        ↓
documented filters
        ↓
eligible settled statuses
        ↓
chronological ordering
        ↓
cumulative calculation
```

Do not calculate a global cumulative series and then remove points afterward if that changes the meaning of the filtered result.

## Filtered cumulative semantics

A filtered bankroll evolution represents the cumulative performance of the filtered dataset.

Therefore, when filters select a subset, the cumulative series must be calculated from that eligible subset in documented order.

Do not leak profit from records excluded by the filters.

## Filter composition

Multiple filters must compose according to the API contract.

Conceptually:

```text
userId
AND filter A
AND filter B
AND ...
```

Ownership must always remain mandatory.

## Date filters

The date filters apply to `settledAt`:

```text
startDate: settledAt >= startDate
endDate:   settledAt <= endDate
```

Both limits are inclusive. Query values use ISO-8601 instant format and are parsed as `Instant`; offset-bearing values representing the same instant are equivalent. When both are supplied, `startDate > endDate` is invalid and returns `400 Bad Request`.

Malformed date values return `400 Bad Request` using the standard validation error response.

Do not apply the date filters to `placedAt`, projection creation time, or projection update time.

## Text/dimension filters

Bankroll evolution supports these dimension filters:

- sport;
- league;
- team;
- market;

use matching semantics exactly as documented.

Do not invent:

- partial matching;
- case-insensitive behavior;
- normalization;
- fuzzy matching.

## Status filter

If the API permits an explicit status filter, it must still respect the bankroll evolution eligibility contract.

Do not allow an invalid/non-performance state to affect cumulative performance merely because the client requested it unless the API/domain documentation explicitly allows that behavior.

## Persistence strategy

The application layer should depend on a neutral Analytics persistence port.

The persistence layer should return the filtered eligible projection data required for bankroll calculation.

An efficient implementation may:

- query ordered projections directly;
- apply appropriate SQL filtering;
- calculate cumulative values in application code.

Avoid pushing stateful cumulative business behavior into opaque SQL unless there is a clear project reason.

The cumulative rule should remain testable.

## Query ordering

The persistence query must explicitly order projections by:

```text
settledAt ASC, betId ASC
```

Do not rely on:

- primary-key order;
- insertion order;
- PostgreSQL physical order.

## API boundary

Expose bankroll evolution through the exact Analytics API endpoint documented in `docs/api-contracts.md`.

Do not invent a duplicate endpoint.

The response must match the documented public representation.

Do not leak internal projection identifiers unless explicitly part of the contract.

## Point representation

Each returned point must contain exactly these public fields:

```text
date
profit
cumulativeProfit
bankroll
```

The `profit` field is the persisted projected profit. `cumulativeProfit` is the sequential sum after filtering and ordering. `bankroll` is equal to `cumulativeProfit`. The point representation must not expose `betId` or other internal projection identifiers.

## Authentication boundary

Use the trusted authenticated-user propagation mechanism established by the architecture.

Missing or malformed authenticated-user identity must return the documented unauthorized behavior.

Never accept ownership from:

- query parameter;
- request body;
- arbitrary header not established by the gateway contract.

## Invalid filters

Invalid filters must produce the documented client error.

Do not silently:

- ignore invalid values;
- broaden them;
- convert them to no filter.

No internal SQL or stack details should leak through the API.

## Read-only behavior

Bankroll evolution is read-only.

A request must never mutate:

- `analytics_bets`;
- `processed_events`.

Do not register analytical reads as processed events.

Do not write precomputed cumulative values to the database in this task.

## No pre-aggregation

Do not create:

- bankroll history table;
- daily aggregate table;
- materialized view;
- precomputed time-series projection;
- background aggregation job.

Task 7.2 calculates the MVP response from existing `analytics_bets`.

## Task 7.1 regression protection

The implementation must not change dashboard summary formulas or API behavior introduced by Task 7.1.

Do not extract shared code in a way that changes:

- metric status eligibility;
- rounding;
- filters;
- response semantics.

Shared neutral filtering/query infrastructure is acceptable only if behavior remains unchanged.

## Task 6.3 regression protection

Do not alter:

- event consumption;
- processed-event idempotency;
- projection lifecycle;
- RabbitMQ listener behavior;
- reject-without-requeue configuration;
- Task 6.3 migrations.

Bankroll evolution is strictly read-side behavior.

## Out of scope

The following are explicitly outside Task 7.2:

- deposits;
- withdrawals;
- bankroll configuration;
- initial bankroll persistence;
- multiple bankroll accounts;
- account balances;
- cash-flow ledger;
- grouped performance breakdowns;
- `groupBy`;
- dashboard summary formula changes;
- exports;
- scheduled reports;
- pre-aggregated time-series tables;
- frontend chart implementation;
- Redis/cache;
- materialized views;
- background aggregation jobs.

## Acceptance criteria

### Evolution series

- [ ] The authenticated user can obtain bankroll evolution through the documented API.
- [ ] Points use only documented eligible settlement states.
- [ ] Points are ordered according to the documented settlement chronology.
- [ ] Cumulative values are calculated sequentially and correctly.
- [ ] Zero-based initial behavior follows the documented contract.
- [ ] Negative cumulative values are preserved.
- [ ] Zero-profit behavior follows the documented eligibility/point rules.

### Settlement data

- [ ] `profit` comes from the Analytics projection.
- [ ] CASHOUT values are not recalculated.
- [ ] No settlement financial formula is duplicated in Analytics.
- [ ] Pending bets do not affect realized performance unless explicitly documented.

### Ordering

- [ ] Chronological ordering uses the documented timestamp.
- [ ] Ordering is deterministic.
- [ ] Same-date/same-timestamp behavior follows the documented rule.
- [ ] Database incidental row order is not relied upon.

### Ownership

- [ ] Every query is scoped to the authenticated user.
- [ ] Cross-user projections never affect returned points.
- [ ] Client-controlled input cannot override ownership.

### Filters

- [ ] All bankroll-evolution filters documented by the API contract are supported.
- [ ] Filters compose correctly.
- [ ] Filtering happens before cumulative calculation where required by the contract.
- [ ] Filters do not bypass ownership.
- [ ] Invalid filter values return the documented client error.

### Decimal behavior

- [ ] Cumulative calculations use `BigDecimal`.
- [ ] No `double` or `float` is used for financial calculations.
- [ ] Scale follows documented rules.
- [ ] Rounding follows documented rules.
- [ ] Negative and zero values are represented correctly.

### Empty behavior

- [ ] No eligible bets return the documented successful empty response.
- [ ] Only ineligible statuses produce the documented empty response.
- [ ] Filters with no matches return the documented empty response.

### Persistence

- [ ] Reads use Analytics-owned `analytics_bets`.
- [ ] Query ordering is explicit.
- [ ] Requests do not mutate `analytics_bets`.
- [ ] Requests do not mutate `processed_events`.
- [ ] Analytics does not access the Betting database.

### API

- [ ] Response shape matches `docs/api-contracts.md`.
- [ ] Missing authenticated identity produces the documented unauthorized response.
- [ ] Invalid filters produce the documented client error.
- [ ] Empty results remain successful when defined by contract.

## Boundary and negative cases

Tests must cover at least the documented variants of:

- no bets;
- only pending bets;
- one settled bet;
- multiple settled bets;
- only wins;
- only losses;
- mixed wins and losses;
- cashout;
- void;
- cancelled;
- zero-profit settlement where eligible;
- negative cumulative performance;
- recovery from negative to positive cumulative performance;
- same-date/same-timestamp settlements;
- cross-user projections;
- single filter;
- multiple filters;
- filters with no matches;
- decimal values requiring documented precision/rounding.

Do not add unsupported bankroll-management behavior merely to increase test count.

## Expected tests

This task uses the normal TDD workflow.

The blind test agent must create Red tests before implementation.

The expected layers are:

### Application/calculation tests

Protect:

- chronological calculation;
- cumulative addition;
- negative values;
- zero values;
- status eligibility;
- filtered cumulative semantics;
- empty data;
- deterministic ordering assumptions defined by the contract;
- BigDecimal behavior.

Fixtures should be designed to detect:

- accidental floating-point conversion;
- wrong ordering;
- use of placedAt instead of settledAt;
- filtering after accumulation;
- recalculation of settlement profit;
- cross-user contamination.

### Persistence integration tests

Use the existing real Analytics persistence strategy.

Protect:

- user scoping;
- documented filters;
- settlement eligibility;
- explicit chronology ordering;
- mixed users;
- mixed statuses;
- same timestamp cases;
- decimal round-trip;
- empty query behavior.

Use PostgreSQL/Testcontainers consistently where database semantics matter.

Do not replace PostgreSQL integration coverage with H2.

### API integration tests

Protect:

- authenticated request;
- documented response structure;
- correctly ordered points;
- cumulative values;
- filters;
- empty response;
- unauthorized request;
- invalid filters;
- user isolation.

Frontend/chart rendering is not part of this task.

## Test quality constraints

Tests must protect behavior rather than arbitrary implementation details.

Do not require without contractual reason:

- exact use-case class names;
- exact repository names;
- exact SQL strings;
- exact DTO class names;
- controller method names;
- private helper methods;
- JDBC versus another valid persistence implementation.

Architecture constraints may be tested where they protect service isolation and layer boundaries.

## Regression expectations

After implementation, verify at minimum:

```bash
./gradlew :services:analytics-service:test --rerun-tasks
./gradlew :services:analytics-service:check --rerun-tasks

./gradlew :libs:messaging-contract:test --rerun-tasks
./gradlew :services:betting-service:check --rerun-tasks
./gradlew :services:auth-service:check --rerun-tasks
./gradlew :services:api-gateway:check --rerun-tasks

./gradlew check --rerun-tasks
```

The implementation must also execute:

```bash
git diff --check
git status --short
git diff
```

New untracked implementation files must be exposed for review only with:

```bash
git add -N <new-file>
```

The implementation agent must not perform regular staging.

## Definition of Done

Apply `docs/definition-of-done.md`.

Task 7.2 is complete only when:

- the specification is satisfied;
- Red tests were created and human-approved;
- implementation makes the approved tests Green;
- Task 7.1 behavior remains Green;
- Task 6.3 behavior remains Green;
- service regressions remain Green;
- independent final QA approves;
- the human approves the QA outcome;
- status reaches `DONE` through the documented state machine.

## Status and evidence

| Field | Value |
| --- | --- |
| Status | `DONE` |
| Red tests | Created: `Task72BankrollEvolutionCalculationTest`, `Task72BankrollEvolutionApiIntegrationTest`, `Task72BankrollEvolutionArchitectureBoundaryTest` |
| Human test approval | Approved |
| Implementation | Green |
| Human implementation approval | Approved |
| Final QA | `APPROVED WITH RESERVATIONS`; human approved the QA outcome on 2026-08-25 |
| Evidence | RED review approved: `./gradlew :services:analytics-service:test --tests '*Task72*' --rerun-tasks` previously reported 9 expected REDs (7 API boundary/404, 2 application boundary) and 4 architecture tests green. Independent QA focused execution passes 18 runtime Task72 tests (11 API, 5 architecture, 2 application); `./gradlew :services:analytics-service:test --rerun-tasks`, `./gradlew :services:analytics-service:check --rerun-tasks`, `./gradlew :libs:messaging-contract:test --rerun-tasks`, the Betting/Auth/Gateway checks, and `./gradlew check --rerun-tasks` all pass. Revalidation confirmed authenticated identity is checked before filter validation in `BankrollEvolutionController`. The remaining documented reservation is that protected Task72 files are untracked and have no Git baseline for semantic diff comparison; the human explicitly accepted this reservation. Human implementation diff review approved; new production files are exposed only with `git add -N`. |

### QA report

VERDICT: APPROVED WITH RESERVATIONS

Human approval: approved on 2026-08-25; task finalized as `DONE`.
