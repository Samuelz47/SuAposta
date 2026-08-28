# 7.3 — Expose filtered performance breakdowns

## Context

Task 7.1 introduces dashboard summary metrics calculated from Analytics-owned projections.

Task 7.2 introduces chronological bankroll evolution.

Task 7.3 adds grouped analytical views over the same `analytics_bets` projection.

The goal is to allow the authenticated user to understand performance segmented by documented dimensions without introducing pre-aggregated reporting infrastructure.

The implementation must continue to follow:

- `docs/domain.md`
- `docs/api-contracts.md`
- `docs/architecture.md`

Those documents remain the source of truth for:

- supported grouping dimensions;
- metric formulas;
- status eligibility;
- filters;
- decimal scale and rounding;
- API response shape;
- authenticated-user isolation.

This task must not invent new groupings or analytical formulas.

## Objective

Expose the authenticated user's filtered performance breakdown grouped by a valid documented `groupBy` dimension.

For every returned group, calculate the documented performance metrics using only Analytics-owned projections and the same metric semantics established in Task 7.1.

## Scope

This task includes:

- validation of the documented `groupBy` values;
- filtered Analytics projection retrieval;
- grouping by supported dimensions;
- grouped metric calculation;
- authenticated-user isolation;
- documented filters;
- decimal scale and rounding;
- deterministic grouped response;
- Analytics API boundary;
- application/calculation tests;
- persistence integration tests;
- API integration tests.

This task does not introduce new event-processing behavior.

## Dependencies

Required completed tasks:

- Task 6.3 — idempotent Analytics projections;
- Task 7.1 — dashboard summary metrics.

Task 7.2 is not a behavioral dependency for grouped performance calculations, but its implementation must remain unaffected if already completed.

Task 6.3 and Task 7.1 behavior must remain unchanged.

## Source of truth

Use `docs/domain.md` for analytical formulas and status eligibility.

Use `docs/api-contracts.md` for:

- supported `groupBy` values;
- request filters;
- response representation;
- validation behavior;
- API endpoint.

If the documents do not clearly define any of the following:

- supported grouping dimensions;
- null-dimension behavior;
- group label representation;
- ordering of groups;
- metric formulas;
- metric denominators;
- status eligibility;
- filter semantics;
- decimal scale;
- rounding;

the implementation agent must stop and report the ambiguity.

Do not invent a grouping rule merely to complete the implementation.

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
- parse and validate `groupBy`;
- parse and validate filters;
- invoke the application use case;
- map the result to the documented API response.

It must not own:

- SQL;
- grouping algorithms;
- metric formulas;
- status eligibility;
- persistence decisions.

The application layer owns grouped analytical behavior.

Persistence infrastructure owns projection querying.

## Service isolation

Analytics must calculate breakdowns only from its own `analytics_bets` projection.

This task must not:

- query the Betting database;
- call Betting repositories;
- call Betting services;
- consume RabbitMQ during the read request;
- synchronously retrieve bet data from another service.

The only source of analytical data is Analytics-owned persistence.

## Authenticated user

Every breakdown query belongs exclusively to the authenticated user.

User identity must come from the trusted authentication boundary already established by the architecture.

Client-controlled input must not select another user's records.

Every persistence query must enforce authenticated `userId`.

Cross-user records must never affect:

- group existence;
- metric values;
- counts;
- filters;
- ordering.

## groupBy contract

The endpoint is:

```http
GET /analytics/performance/breakdown
```

`groupBy` is required and accepts exactly these case-sensitive values:

```text
SPORT
LEAGUE
TEAM
MARKET
MONTH
WEEK
DAY
```

Matching is exact. The implementation must not trim, normalize, case-fold, or
otherwise reinterpret the value. `sport`, `Sport`, `" SPORT "`, and unknown
values are invalid.

## Invalid groupBy

An unsupported, blank, or otherwise invalid `groupBy` must return:

```text
400
```

or the exact documented client-error representation.

Do not:

- silently fall back to a default grouping;
- treat an invalid value as no grouping;
- ignore the parameter;
- expose internal enum/parser exceptions.

## Missing groupBy

`groupBy` is mandatory. A missing value must return `400 Bad Request`. There is
no default grouping.

## Grouping behavior

For each valid grouping dimension:

```text
authenticated user's analytics_bets
        ↓
filters
        ↓
grouping
        ↓
metric calculation per bucket
```

Filters must be applied before grouping and metric calculation. Each metric is
then calculated independently within each bucket.

For `SPORT`, `LEAGUE`, and `MARKET`, each projection belongs to one bucket
identified by the corresponding persisted value. For `TEAM`, one projection may
contribute to both its `homeTeam` and `awayTeam` buckets, as defined below.

### SPORT grouping

`SPORT` uses exactly `AnalyticsBet.sport`. Each bet belongs to one SPORT bucket
and `name` receives the persisted `sport` value.

### LEAGUE grouping

`LEAGUE` uses exactly `AnalyticsBet.league`. Each bet belongs to one LEAGUE
bucket and `name` receives the persisted `league` value.

### MARKET grouping

`MARKET` uses exactly `AnalyticsBet.market`. Each bet belongs to one MARKET
bucket and `name` receives the persisted `market` value.

### DAY grouping

`DAY` uses `placedAt` converted to UTC. The bucket name is `YYYY-MM-DD`, and
each bet belongs to one DAY bucket.

### MONTH grouping

`MONTH` uses `placedAt` converted to UTC. The bucket name is `YYYY-MM`, and each
bet belongs to one MONTH bucket.

### WEEK grouping

`WEEK` uses `placedAt` converted to UTC and ISO-8601 week-based-year semantics.
The bucket name is `YYYY-Www`, where the year is the ISO week-based-year rather
than necessarily the calendar year. Each bet belongs to one WEEK bucket.

## Metric reuse

Grouped metrics must use the same formulas and eligibility semantics established by Task 7.1.

Every item contains exactly these 14 metrics:

```text
totalStake
profit
roi
yield
winRate
avgOdds
drawdown
betsCount
pendingCount
wonCount
lostCount
voidCount
cashoutCount
cancelledCount
```

`profit` is the projected profit aggregate, `avgOdds` is the unweighted
arithmetic mean of projected odds, and `drawdown` is the maximum absolute
money drawdown computed within the bucket using the Task 7.1 chronology.
`totalReturn` is not introduced.

Do not introduce a second interpretation of:

- total stake;
- profit;
- ROI;
- yield;
- win rate;
- average odds;
- counts.

If Task 7.1 centralizes metric calculation in a reusable application component, reusing it is encouraged where behavior remains explicit and testable.

Do not create divergent formulas inside the breakdown use case.

## Metric-specific status eligibility

Do not assume that every grouped metric uses the same set of statuses.

Each group must apply the documented eligibility rules for each metric exactly as Task 7.1 does.

Explicitly preserve the documented treatment of:

- `PENDING`;
- `WON`;
- `LOST`;
- `VOID`;
- `CASHOUT`;
- `CANCELLED`.

`PENDING` contributes to `betsCount` and `pendingCount` only. `VOID` and
`CANCELLED` contribute to `betsCount` and their own status counts only.
`CASHOUT` participates in turnover, profit, ROI, yield, average odds, and
drawdown using projected values, but not in win rate.

## CASHOUT

Use the Analytics projection values already produced by Task 6.3.

Do not recalculate:

- profit;
- return amount.

Grouped performance aggregates projected values; it does not repeat settlement business logic.

## Null dimension values

For `SPORT`, `LEAGUE`, and `MARKET`, a null or blank group value does not
create a bucket. For `TEAM`, `homeTeam` and `awayTeam` are evaluated
independently; a null or blank value creates no contribution for that side.

Do not create synthetic buckets named `UNKNOWN`, `N/A`, `null`, or the empty
string.

## Team grouping

`TEAM` uses exactly `homeTeam` and `awayTeam`. A bet contributes to one bucket
for `homeTeam` and one bucket for `awayTeam` when those values are non-null and
non-blank. `selection` never participates in TEAM grouping.

If `homeTeam` and `awayTeam` are exactly equal, the bet contributes only once
to that bucket. TEAM bucket metrics represent all bets in which the team
appears as home or away. Consequently, TEAM bucket metrics are not globally
additive and the sum of TEAM `betsCount` values need not equal the global
`betsCount`.

## Group identity

Each returned group must expose the group identifier/label exactly as documented.

Do not expose internal persistence IDs unless the API contract includes them.

Do not normalize group values unless explicitly documented.

## Deterministic ordering

For `SPORT`, `LEAGUE`, `TEAM`, and `MARKET`, order groups by `name ASC` using
natural/exact comparison consistent with persisted values. For `DAY`, `WEEK`,
and `MONTH`, order groups chronologically ascending. Do not depend on database
incidental order or insertion order.

## Financial precision

Use `BigDecimal` for:

- total stake;
- profit;
- ROI;
- yield;
- win rate where represented as decimal;
- average odds;
- any decimal grouped result.

Do not use:

- `double`;
- `float`.

This includes intermediate calculations.

## Scale and rounding

Grouped metrics must use the same documented scale and rounding behavior as Task 7.1.

Do not round per-row values before aggregation unless the domain contract explicitly requires it.

Do not invent a separate rounding mode for breakdowns.

Normalize decimal results at the documented result boundary: money uses scale
`2`, percentages use scale `2`, odds use scale `4`, and all use `HALF_UP`.
Use `BigDecimal` for stored values, intermediate calculations, and results.

## Zero denominators

Each group must independently handle zero denominators.

Examples may include:

- group with zero eligible stake;
- group with no eligible win-rate denominator;
- group with no eligible odds.

Return the documented zero/default value.

Do not produce:

- divide-by-zero exception;
- `NaN`;
- infinity;
- undocumented nulls.

## Negative performance

Groups may have negative:

- profit;
- ROI;
- yield.

Negative values are valid and must not be clamped to zero.

## Empty result

If no projection matches:

- authenticated user;
- filters;
- grouping eligibility;

return the documented successful empty response.

Do not fabricate an empty group.

Do not return another user's group.

The exact response is:

```json
{
  "groupBy": "<requested valid groupBy>",
  "items": []
}
```

Return only groups observed after ownership and filters. Do not generate
synthetic zero-valued groups for absent dimensions.

## Filters

Task 7.3 accepts only these performance-breakdown filters:

```text
startDate
endDate
sport
league
market
```

It does not accept `team`, `status`, `minOdds`, `maxOdds`, `minStake`, or
`maxStake` as breakdown filters.

Filters must apply before grouping and metric calculation.

Conceptually:

```text
userId
AND filter A
AND filter B
AND ...
        ↓
group
        ↓
aggregate
```

Do not calculate global groups and filter the groups afterward if that changes metric values.

## Filter composition

Multiple filters must compose according to the API contract.

Ownership remains mandatory regardless of supplied filters.

A filter must never replace or weaken the authenticated-user predicate.

## Date filters

Task 7.3 date filters use `placedAt`:

```text
startDate: placedAt >= startDate
endDate:   placedAt <= endDate
```

Both boundaries are inclusive. Query values are ISO-8601 instants parsed as
`Instant`; offset-bearing values representing the same instant are equivalent.
Malformed values and `startDate > endDate` return `400 Bad Request`.

`settledAt` is not a Task 7.3 date-filter field. It remains the chronology used
by the Task 7.1 drawdown calculation inside each already-filtered bucket.

## Status filters

Status filtering is not exposed by Task 7.3. Statuses are used only by the
documented per-metric eligibility and status-count rules.

## Dimension filters

Task 7.3 text filters are `sport`, `league`, and `market`. They use exact,
case-sensitive equality with the persisted value. The server must not trim,
normalize, partially match, or case-fold them. Blank values return
`400 Bad Request`.

Filters and grouping may involve the same dimension.

Example conceptually:

```text
filter sport = FOOTBALL
groupBy = league
```

This must produce groups only from projections satisfying the filter.

Do not ignore filters because a grouping is active.

## Persistence strategy

The application should depend on a neutral Analytics persistence port.

Persistence may return:

- filtered projection rows;
- a projection view appropriate for analytical calculation;
- safe aggregate input.

The implementation may use SQL grouping if appropriate, but metric semantics must remain explicit and testable.

Do not create pre-aggregated reporting tables.

## SQL grouping

If SQL aggregation/grouping is used:

- user scoping must be mandatory;
- filters must be parameterized;
- monetary calculations must remain NUMERIC/DECIMAL;
- null grouping behavior must match the contract;
- status eligibility must remain correct;
- application/API semantics must remain testable.

Do not generate grouping column names directly from raw client input.

A valid `groupBy` must map through a controlled whitelist/enum/strategy before affecting SQL.

## SQL injection protection

`groupBy` is a structural query choice and must never be concatenated blindly from client input.

If dynamic SQL is required, map each documented `groupBy` value to an explicitly controlled persistence representation.

Invalid values must be rejected before query execution.

Do not interpolate arbitrary client strings as SQL identifiers.

## API boundary

Expose the performance breakdown through the exact endpoint documented in `docs/api-contracts.md`.

Do not invent duplicate endpoints for individual grouping dimensions.

The public API should use the documented `groupBy` contract.

## Response structure

The top-level response contains exactly `groupBy` and `items`. `groupBy` echoes
the valid requested value. Each item contains exactly `name` plus these 14
metrics, with no additional fields:

```text
totalStake
profit
roi
yield
winRate
avgOdds
drawdown
betsCount
pendingCount
wonCount
lostCount
voidCount
cashoutCount
cancelledCount
```

`totalReturn` is not part of the response.

Do not leak:

- internal projection IDs;
- SQL aliases;
- database column names not part of the API.

## Authentication boundary

Use the trusted `X-User-Id` authenticated-user propagation mechanism
established by the architecture.

Missing or malformed UUID identity must return `401 Unauthorized`. Identity
validation has precedence over filter and `groupBy` validation: a request with
both an absent/malformed `X-User-Id` and an invalid filter must return `401`.

Ownership comes only from `X-User-Id`. Never accept it from:

- request body;
- public query `userId`;
- any other client-controlled field.

## Error handling

Invalid:

- `groupBy`;
- filter values;
- enum values;
- request format;

must return the documented client error.

Do not expose:

- SQL exceptions;
- stack traces;
- internal repository names;
- database syntax details.

## Read-only behavior

Breakdown requests are read-only.

They must never mutate:

- `analytics_bets`;
- `processed_events`.

Do not persist calculated groups.

Do not register read requests as processed events.

## No pre-aggregated tables

Do not introduce:

- grouped performance table;
- sport aggregate table;
- league aggregate table;
- team aggregate table;
- market aggregate table;
- materialized views;
- scheduled aggregate jobs;
- cache-backed reporting storage.

Task 7.3 computes the MVP breakdown from existing projections.

## Task 7.1 regression protection

Grouped metrics must remain semantically identical to dashboard metrics.

Do not alter Task 7.1 formulas or status rules merely to share implementation.

Any extracted reusable calculation component must preserve all approved Task 7.1 tests.

## Task 7.2 regression protection

If Task 7.2 is already complete, do not alter:

- bankroll chronology;
- cumulative calculation;
- settled-status behavior;
- bankroll API contract.

Breakdowns are independent read behavior.

## Task 6.3 regression protection

Do not alter:

- RabbitMQ consumption;
- durable idempotency;
- concurrent duplicate behavior;
- lifecycle handling;
- projection persistence;
- `processed_events`;
- reject-without-requeue behavior;
- Task 6.3 migrations.

## Out of scope

The following are explicitly outside Task 7.3:

- bankroll evolution changes;
- dashboard-summary formula changes;
- exports;
- CSV/PDF reports;
- scheduled reports;
- pre-aggregated tables;
- background analytics jobs;
- materialized views;
- Redis/cache;
- frontend charts;
- custom user-defined groupings;
- nested/multiple simultaneous `groupBy` dimensions;
- pivot tables;
- arbitrary SQL dimensions;
- forecasting;
- betting recommendations.

## Acceptance criteria

### Grouping

- [ ] Every documented `groupBy` value is supported.
- [ ] Unsupported `groupBy` returns the documented `400` response.
- [ ] Missing or blank `groupBy` returns `400`, with no trimming or normalization.
- [ ] Each projection is assigned according to the documented grouping semantics.
- [ ] TEAM uses `homeTeam` and `awayTeam`, never `selection`, and suppresses a duplicate same-team contribution.
- [ ] DAY, MONTH, and WEEK use UTC `placedAt`, including ISO week-based-year semantics for WEEK.
- [ ] Null dimension behavior follows the documented contract.
- [ ] Returned groups are ordered deterministically according to the API contract.

### Metrics

- [ ] Group metrics reuse Task 7.1 formulas.
- [ ] Total stake is correct per group.
- [ ] Profit is correct per group.
- [ ] ROI is correct per group.
- [ ] Yield is correct per group.
- [ ] Win rate is correct per group.
- [ ] `avgOdds` is correct per group.
- [ ] `drawdown` follows Task 7.1 chronology within each bucket.
- [ ] `betsCount`, `pendingCount`, `wonCount`, `lostCount`, `voidCount`, `cashoutCount`, and `cancelledCount` are correct per group.
- [ ] Metric-specific status eligibility remains correct.

### Ownership

- [ ] Every query is scoped to the authenticated user.
- [ ] Cross-user projections do not create groups.
- [ ] Cross-user projections do not affect group metrics.
- [ ] Client input cannot override authenticated ownership.

### Filters

- [ ] Only `startDate`, `endDate`, `sport`, `league`, and `market` are supported as performance filters.
- [ ] Filters are applied before grouping/aggregation.
- [ ] Multiple filters compose correctly.
- [ ] Filters do not bypass ownership.
- [ ] Date filters use inclusive `placedAt` ISO-8601 instant boundaries and reject malformed values and invalid ranges.
- [ ] Text filters are exact, case-sensitive, non-blank, and are not trimmed, normalized, or partially matched.
- [ ] Filters with no matches return the documented empty result.
- [ ] Invalid filter values return the documented client error.

### Decimal behavior

- [ ] Financial and decimal calculations use `BigDecimal`.
- [ ] No `double` or `float` arithmetic is used.
- [ ] Scale matches the documented rules.
- [ ] Rounding matches the documented rules.
- [ ] Negative grouped performance is preserved.
- [ ] Zero denominators return documented zero/default values.

### Persistence

- [ ] Reads use Analytics-owned `analytics_bets`.
- [ ] No Betting database access is introduced.
- [ ] Dynamic grouping cannot interpolate arbitrary client input into SQL.
- [ ] Requests do not mutate `analytics_bets`.
- [ ] Requests do not mutate `processed_events`.

### API

- [ ] Response structure matches `docs/api-contracts.md`.
- [ ] The top-level response contains exactly `groupBy` and `items`, and each item contains exactly `name` plus the 14 documented metrics.
- [ ] Invalid `groupBy` returns `400`.
- [ ] Missing or malformed `X-User-Id` returns `401` before filter validation.
- [ ] Empty results remain successful when defined by contract.
- [ ] Internal persistence details are not exposed.

## Boundary and negative cases

Tests must cover at least the documented variants of:

- empty dataset;
- one group;
- multiple groups;
- mixed positive and negative groups;
- group with zero denominator;
- mixed statuses;
- cashout;
- void;
- cancelled;
- pending data;
- null grouping dimension;
- valid grouping;
- every supported grouping dimension;
- invalid, blank, and missing grouping;
- case-sensitive grouping values without trimming or normalization;
- cross-user data;
- single filter;
- multiple filters;
- filters with no matches;
- malformed date, reversed date range, and equivalent instant offsets;
- blank, partial, normalized, and case-variant text filters;
- invalid identity taking precedence over invalid filters;
- grouping and filtering by different dimensions;
- decimal values requiring documented rounding.

TEAM tests must explicitly protect its documented home/away semantics.

Do not invent unsupported grouping behavior merely to increase coverage.

## Expected tests

This task uses the normal TDD workflow.

The blind test agent must create Red tests before production implementation.

The expected layers are:

### Application/calculation tests

Protect:

- grouping behavior;
- metric reuse;
- status eligibility;
- zero denominators;
- negative values;
- empty groups;
- null-dimension behavior;
- decimal precision;
- deterministic ordering where application-owned.

Fixtures should detect:

- formulas diverging from Task 7.1;
- projections placed in wrong groups;
- accidental cross-user aggregation;
- use of floating-point arithmetic;
- premature rounding;
- inclusion of ineligible statuses.

### Persistence integration tests

Use the existing real Analytics persistence strategy.

Protect:

- authenticated-user scoping;
- documented filters;
- each supported grouping dimension where persistence-owned;
- mixed users;
- mixed statuses;
- null dimensions;
- decimal round-trip;
- empty results;
- SQL-safe group mapping.

Use PostgreSQL/Testcontainers consistently with the existing Analytics persistence strategy.

Do not replace relevant PostgreSQL integration coverage with H2.

### API integration tests

Protect:

- authenticated request;
- every supported `groupBy`;
- invalid `groupBy`;
- missing `groupBy`;
- filters;
- metric values;
- response structure;
- empty result;
- unauthorized request;
- user isolation.

Frontend integration is not part of this task.

## Test quality constraints

Tests must protect behavior rather than arbitrary implementation details.

Do not require without contractual reason:

- exact use-case class names;
- exact repository names;
- exact SQL text;
- exact enum class names;
- exact DTO names;
- controller method names;
- private methods;
- JDBC versus another valid persistence implementation.

Tests may explicitly protect SQL injection safety at the public/persistence boundary without freezing exact SQL.

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

New untracked implementation files must be exposed for human/final-QA review only with:

```bash
git add -N <new-file>
```

The implementation agent must not perform regular staging.

## Definition of Done

Apply `docs/definition-of-done.md`.

Task 7.3 is complete only when:

- the specification is satisfied;
- Red tests were created and human-approved;
- implementation makes the approved tests Green;
- Task 7.1 behavior remains Green;
- Task 7.2 behavior remains Green if already completed;
- Task 6.3 behavior remains Green;
- service regressions remain Green;
- independent final QA approves;
- the human approves the QA outcome;
- status reaches `DONE` through the documented state machine.

## Status and evidence

| Field | Value |
| --- | --- |
| Status | `DONE` |
| Red tests | Approved. Created in `Task73PerformanceBreakdownApiIntegrationTest`, `Task73PerformanceBreakdownCalculationTest`, `Task73PerformanceBreakdownArchitectureBoundaryTest`, and `Task73TestSupport`. |
| Human test approval | Approved |
| Implementation | Green — protected Task 7.3 tests passed (46/46), and all required focused, service, external-service, and two root regressions passed. |
| Human implementation approval | Approved on 2026-08-27. |
| Final QA | `APPROVED WITH RESERVATIONS`; human approved the QA outcome and authorized finalization on 2026-08-27. |
| Evidence | Initial RED: `compileTestJava` passed; 45 Task 7.3 tests produced 41 expected failures (40 endpoint `404`s and one missing application seam), with four architecture tests Green. The implementer corrected canonical `X-User-Id` UUID validation and added the approved regression for non-canonical UUID input; the protected Task73 suite then passed 46/46. Task 7.2, Task 7.1, Task 6.3, full Analytics test/check, messaging-contract, Betting, Auth, Gateway, and root checks passed. No protected expectation was weakened, no dependency or migration was added, and new production files were exposed only with `git add -N`. Human approved the implementation diff and the QA reservation on 2026-08-27. |
