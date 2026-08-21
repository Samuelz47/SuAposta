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

The endpoint must accept only the grouping dimensions explicitly documented in `docs/api-contracts.md`.

Examples of possible analytical dimensions may include:

- sport;
- league;
- team;
- market.

This list is conceptual only.

Do not treat a dimension as supported unless the API contract explicitly defines it.

## Invalid groupBy

An unsupported `groupBy` must return:

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

If the API contract requires `groupBy`, a missing value must produce the documented client error.

If a default grouping is explicitly documented, use only that documented default.

Do not invent one.

## Grouping behavior

For each valid grouping dimension:

```text
authenticated user's projections
        ↓
documented filters
        ↓
metric-specific eligibility
        ↓
group by requested dimension
        ↓
calculate documented metrics per group
```

Each projection must participate only in the group implied by its documented dimension value.

Do not duplicate one projection across multiple groups unless the source-of-truth contract explicitly defines that behavior.

## Metric reuse

Grouped metrics must use the same formulas and eligibility semantics established by Task 7.1.

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

## CASHOUT

Use the Analytics projection values already produced by Task 6.3.

Do not recalculate:

- profit;
- return amount.

Grouped performance aggregates projected values; it does not repeat settlement business logic.

## Null dimension values

A projection may have a null value for a grouping dimension depending on the documented schema/domain.

The behavior for null dimension values must follow `docs/domain.md` or `docs/api-contracts.md`.

Possible documented behaviors could include:

- exclude the record from that grouping;
- return a dedicated null/unknown group;
- use a documented label.

Do not invent a label such as:

```text
Unknown
N/A
Other
```

unless explicitly documented.

If null grouping behavior is not documented and the persisted schema permits it, stop and report the ambiguity.

## Team grouping

If `team` is a supported grouping dimension, follow the exact domain/API definition of what constitutes the team value.

Do not independently decide whether team means:

- home team;
- away team;
- selected team;
- either participating team;
- normalized team name.

The contract must define this.

If it does not, stop and report the ambiguity.

## Group identity

Each returned group must expose the group identifier/label exactly as documented.

Do not expose internal persistence IDs unless the API contract includes them.

Do not normalize group values unless explicitly documented.

## Deterministic ordering

Returned groups must be ordered deterministically according to `docs/api-contracts.md`.

If the API contract defines ordering such as:

- alphabetical;
- descending profit;
- descending stake;
- explicit domain ordering;

follow it exactly.

If deterministic ordering is required but no ordering rule exists, stop and report the ambiguity instead of relying on database incidental order.

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

## Filters

Support every performance-breakdown filter documented in `docs/api-contracts.md`.

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

If date filters are supported, apply them to the exact timestamp defined by the contract.

Do not independently choose:

- placedAt;
- settledAt;
- createdAt;
- updatedAt.

If the source-of-truth documents do not define the timestamp for performance filtering, stop and report the ambiguity.

## Status filters

If status filtering is exposed publicly, valid values must be documented.

An explicit status filter must still preserve metric semantics.

Do not allow an invalid status to enter calculations merely because it was passed by the client.

## Dimension filters

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

Each group must expose exactly the documented response fields.

Conceptually, a grouped result may contain:

```text
group
metrics...
```

but the exact response shape comes exclusively from `docs/api-contracts.md`.

Do not leak:

- internal projection IDs;
- SQL aliases;
- database column names not part of the API.

## Authentication boundary

Use the trusted authenticated-user propagation mechanism established by the architecture.

Missing or malformed authentication identity must return the documented unauthorized response.

Never accept user ownership from:

- request body;
- public query `userId`;
- untrusted arbitrary header.

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
- [ ] Missing `groupBy` follows the documented validation behavior.
- [ ] Each projection is assigned according to the documented grouping semantics.
- [ ] Null dimension behavior follows the documented contract.
- [ ] Returned groups are ordered deterministically according to the API contract.

### Metrics

- [ ] Group metrics reuse Task 7.1 formulas.
- [ ] Total stake is correct per group.
- [ ] Profit is correct per group.
- [ ] ROI is correct per group.
- [ ] Yield is correct per group.
- [ ] Win rate is correct per group.
- [ ] Average odds is correct per group.
- [ ] Documented counts are correct per group.
- [ ] Metric-specific status eligibility remains correct.

### Ownership

- [ ] Every query is scoped to the authenticated user.
- [ ] Cross-user projections do not create groups.
- [ ] Cross-user projections do not affect group metrics.
- [ ] Client input cannot override authenticated ownership.

### Filters

- [ ] Every documented performance filter is supported.
- [ ] Filters are applied before grouping/aggregation.
- [ ] Multiple filters compose correctly.
- [ ] Filters do not bypass ownership.
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
- [ ] Invalid `groupBy` returns `400`.
- [ ] Missing authenticated identity returns documented unauthorized behavior.
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
- invalid grouping;
- missing grouping where required;
- cross-user data;
- single filter;
- multiple filters;
- filters with no matches;
- grouping and filtering by different dimensions;
- decimal values requiring documented rounding.

If team grouping exists, tests must explicitly protect its documented semantics.

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
- missing `groupBy` where applicable;
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
| Status | `PLANNED` |
| Red tests | Not started |
| Human test approval | Pending |
| Implementation | Not started |
| Human implementation approval | Pending |
| Final QA | Pending |
| Evidence | Pending |