# 7.1 — Calculate dashboard summary metrics

## Context

Task 6.3 established the Analytics projection consumed from betting lifecycle events.

The Analytics Service now owns a durable `analytics_bets` projection containing the data required to calculate the first MVP analytical metrics without accessing the Betting Service database.

This task introduces the first read-side analytical use case.

The dashboard summary must calculate metrics from Analytics-owned projections while following the formulas, status inclusion rules, filters, ownership rules, decimal behavior, and API representation already documented by:

- `docs/domain.md`
- `docs/api-contracts.md`
- `docs/architecture.md`

Those documents remain the source of truth for metric semantics.

This task must not redefine formulas that are already documented there.

## Objective

Expose a filtered dashboard summary for the authenticated user using only Analytics-owned projections.

The summary must provide the documented dashboard metrics with deterministic decimal behavior, correct status inclusion/exclusion, user isolation, and documented empty-result semantics.

## Scope

This task includes:

- application-level dashboard summary calculation;
- Analytics projection querying;
- authenticated-user isolation;
- documented dashboard filters;
- documented metric formulas;
- documented settlement-status eligibility;
- deterministic decimal scale and rounding;
- Analytics API boundary for the summary;
- unit tests for calculations;
- persistence integration tests;
- API integration tests.

This task does not introduce new event processing behavior.

## Dependencies

Required completed tasks:

- Task 6.1 — messaging contract and topology;
- Task 6.2 — betting lifecycle publication;
- Task 6.3 — idempotent Analytics projections.

Task 6.3 must remain behaviorally unchanged.

## Source of truth

Metric calculations must follow `docs/domain.md`.

API request/response behavior must follow `docs/api-contracts.md`.

If either document is insufficient to determine any of the following:

- formula;
- denominator;
- eligible statuses;
- filter semantics;
- decimal scale;
- rounding mode;
- response field;
- empty-result representation;

the implementation agent must stop and report the ambiguity.

Do not invent a new analytical rule inside this task.

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
- parse and validate request filters;
- invoke the application use case;
- map the application result to the API response.

It must not own:

- SQL;
- aggregate formulas;
- status eligibility rules;
- metric calculations;
- persistence decisions.

The application layer owns analytical orchestration and metric behavior.

The persistence infrastructure owns queries against `analytics_bets`.

## Service isolation

Analytics must continue to use only its own projection database.

This task must not access:

- Betting database;
- Betting repositories;
- Betting entities;
- Betting application services;
- RabbitMQ for read-time metric calculation.

No synchronous request to Betting Service may be introduced.

The dashboard is calculated from the Analytics read model produced by Task 6.3.

## Authenticated user

The dashboard summary belongs exclusively to the authenticated user.

The user identity must come from the trusted boundary already established by the system architecture.

Client-controlled request data must not be allowed to select another user's analytics records.

Every persistence query used by this task must be scoped by the authenticated `userId`.

Cross-user records must never affect:

- totals;
- averages;
- rates;
- counts;
- filtered results.

## Dashboard metrics

Return every summary metric documented for the MVP dashboard.

The required metrics include:

- total stake;
- profit;
- ROI;
- yield;
- win rate;
- average odds;
- documented counts;
- maximum drawdown;
- current drawdown.

The implementation must use the exact formulas defined in `docs/domain.md`.

Do not create alternative interpretations of:

- ROI;
- yield;
- win rate;
- average odds.

If two metrics intentionally share a formula according to current domain documentation, preserve the documented behavior rather than introducing a new distinction in this task.

`maxDrawdown` and `currentDrawdown` are summary metrics in this task. They use the filtered cumulative-profit contract in `docs/domain.md`; Task 7.2 remains responsible for exposing bankroll-evolution time-series points.

## Status eligibility

`analytics_bets` may contain lifecycle states including:

- `PENDING`
- `WON`
- `LOST`
- `VOID`
- `CASHOUT`
- `CANCELLED`

Each metric must include or exclude those statuses exactly as defined in `docs/domain.md`.

In particular, verify explicitly the documented treatment of:

- `PENDING`;
- `VOID`;
- `CANCELLED`;
- `CASHOUT`.

Do not assume that all metrics operate over the same status set.

A bet may be eligible for one metric and excluded from another if that is what the domain documentation defines.

## Pending bets

Pending bets must not accidentally participate in settled-performance calculations when the documented metric requires settled results.

If the API exposes a count that intentionally includes pending bets, follow the documented rule for that specific field.

Do not derive one global status filter and reuse it blindly for all metrics unless the domain contract explicitly defines that behavior.

## VOID and CANCELLED

The treatment of `VOID` and `CANCELLED` must follow the documented metric semantics.

Do not automatically treat them as:

- wins;
- losses;
- profitable bets;
- losing bets;

unless explicitly documented.

Their stake and return values must participate only where the documented formula requires them.

## CASHOUT

`CASHOUT` values must use the projected values already produced by Betting and consumed in Task 6.3.

Analytics must not recalculate:

- `profit`;
- `returnAmount`.

The dashboard calculation may aggregate those values according to the documented formulas, but it must not derive settlement values again from stake or odds.

## Financial precision

Use `BigDecimal` for financial and decimal calculations.

Do not use:

- `double`;
- `float`.

This includes intermediate calculations.

Required decimal behavior must follow the documented scale and rounding rules.

Do not rely on database floating-point arithmetic.

## Division and zero denominators

Metrics involving division must handle zero denominators deterministically.

Examples may include:

- ROI with zero eligible stake;
- yield with zero denominator;
- win rate with zero eligible result count;
- average odds with zero eligible bets.

The returned values must match the empty/zero behavior documented by the domain and API contracts.

No calculation may produce:

- division-by-zero exception;
- `NaN`;
- infinity;
- `null` where the contract defines a numeric zero.

## Negative performance

Negative performance is valid.

A filtered result may produce:

- negative total profit;
- negative ROI;
- negative yield;

when supported by the underlying projection data.

Do not clamp analytical values to zero.

## Average odds

Average odds must be calculated only from the bet set defined by the domain rules.

Use decimal arithmetic.

Do not:

- average using `double`;
- silently include ineligible statuses;
- round individual odds before aggregation unless documented.

Apply rounding at the documented calculation boundary.

## Counts

Return the documented summary counts.

Counts must be calculated independently from monetary aggregates when their eligibility rules differ.

Do not infer count values from unrelated totals.

## Empty dataset

When the authenticated user has no projection row in the filtered set, return the documented successful empty summary.

The endpoint must not fail merely because no rows exist.

The empty result must contain the documented zero/default metric values.

Do not return another user's data.

Do not fabricate synthetic bets.

## Filters

Support the dashboard filters documented in `docs/api-contracts.md`.

Filters must operate exclusively on the authenticated user's Analytics projections.

Only filters belonging to the current dashboard-summary contract are part of this task.

Do not introduce filters merely because equivalent Betting filters exist.

## Filter composition

Multiple supplied filters must compose according to the API contract.

Conceptually:

```text
authenticated user
AND filter A
AND filter B
AND ...
```

A filter must not override the ownership predicate.

## Date filters

If the dashboard contract exposes date-range filters, apply the exact timestamp/date field and boundary semantics documented by the API/domain contracts.

Do not independently choose between:

- `placedAt`;
- `settledAt`;
- `createdAt`;
- `updatedAt`.

If the documentation does not establish which field defines a dashboard date filter, stop and report the ambiguity.

## Enum/status filters

If the API contract accepts status or other enumerated filters:

- valid documented values must be accepted;
- invalid values must produce the documented client error;
- persistence must not silently reinterpret unknown values.

Do not broaden accepted values beyond the contract.

## Text filters

If documented filters include textual dimensions such as:

- sport;
- league;
- team;
- market;

preserve the matching semantics defined by the API contract.

Do not invent case-insensitive, partial, fuzzy, or normalized matching unless documented.

## Persistence strategy

The application layer should depend on a neutral Analytics persistence port.

The implementation may choose an efficient aggregate-query design or load a filtered projection set and calculate in application code, provided that:

- the formulas remain centralized and testable;
- user isolation is guaranteed;
- decimal correctness is preserved;
- the design remains appropriate for the MVP;
- no behavior is moved into the controller.

Do not introduce pre-aggregated tables in this task.

## SQL aggregation

If aggregate SQL is used:

- monetary columns must remain numeric/decimal;
- null aggregate results must be handled explicitly;
- user filtering must be mandatory;
- metric semantics must remain traceable to the domain rules.

Do not rely on database-specific floating-point conversions.

## API boundary

Expose the dashboard summary through the Analytics API contract documented in `docs/api-contracts.md`.

Do not invent a second endpoint for the same summary.

The response must expose exactly the documented public fields.

Internal persistence identifiers must not leak unless part of the API contract.

## Authentication boundary

Use the same trusted authenticated-user propagation pattern already established by the architecture.

Missing or malformed authenticated-user identity must return the documented unauthorized behavior.

Do not fall back to:

- arbitrary user;
- first database user;
- request-body userId;
- query-parameter userId.

## Validation

Invalid request filters must fail at the appropriate boundary.

Use the error semantics already established for the Analytics/API architecture.

Do not expose:

- SQL errors;
- stack traces;
- internal repository names.

## Error handling

Expected client-caused validation failures must result in the documented client response.

Unexpected infrastructure failures may propagate to the existing safe error boundary.

Do not create a new global error architecture unless required by the existing API contract.

## Transaction behavior

This task is read-only from the dashboard perspective.

It must not mutate:

- `analytics_bets`;
- `processed_events`.

A dashboard request must never register an event or modify a projection.

If transaction annotations are used for reads, they must not interfere with the Task 6.3 write transaction behavior.

## Task 6.3 regression protection

The implementation must not weaken:

- event consumption;
- durable idempotency;
- concurrent duplicate handling;
- lifecycle conflict behavior;
- projection persistence;
- processed-event registration;
- RabbitMQ topology;
- reject-without-requeue behavior.

No Task 6.3 migration may be rewritten.

## Out of scope

The following are explicitly outside Task 7.1:

- bankroll evolution;
- cumulative bankroll series;
- time-series points;
- performance grouping;
- `groupBy`;
- sport/league/team breakdown response structures;
- pre-aggregated reporting tables;
- exports;
- scheduled reports;
- deposits;
- withdrawals;
- multiple bankrolls;
- frontend dashboard implementation;
- caching;
- Redis;
- materialized views;
- background aggregation jobs.

Those belong to later tasks or phases.

## Acceptance criteria

### Summary calculation

- [ ] The authenticated user can obtain the dashboard summary defined in the API contract.
- [ ] Total stake follows the documented formula.
- [ ] Profit follows the documented formula.
- [ ] ROI follows the documented formula.
- [ ] Yield follows the documented formula.
- [ ] Win rate follows the documented formula.
- [ ] Average odds follows the documented formula.
- [ ] Every documented count follows its documented eligibility rule.
- [ ] Maximum drawdown follows the documented chronological cumulative-profit formula.
- [ ] Current drawdown follows the documented final-peak formula.

### Status rules

- [ ] `PENDING` participation follows the documented metric rules.
- [ ] `VOID` participation follows the documented metric rules.
- [ ] `CANCELLED` participation follows the documented metric rules.
- [ ] `CASHOUT` participation follows the documented metric rules.
- [ ] Metric-specific status sets are preserved where applicable.

### Ownership

- [ ] Every query is scoped to the authenticated user.
- [ ] Another user's projections never affect the summary.
- [ ] Client input cannot override the authenticated user identity.

### Filters

- [ ] All dashboard-summary filters documented by the API contract are supported.
- [ ] Multiple filters compose correctly.
- [ ] Filters do not bypass user isolation.
- [ ] Date filters use inclusive `placedAt` boundaries.
- [ ] Text and team filters follow the documented exact-match semantics.
- [ ] Odds, stake, and date ranges follow the documented normalization and validation rules.
- [ ] Invalid documented filter values return the documented client error.

### Decimal behavior

- [ ] Financial calculations use `BigDecimal`.
- [ ] Decimal scale follows the documented rules.
- [ ] Rounding follows the documented rules.
- [ ] Negative results remain negative when mathematically valid.
- [ ] No floating-point arithmetic is used for analytical values.

### Zero and empty behavior

- [ ] Empty projection data returns the documented zero/default summary.
- [ ] Zero-denominator metrics return the documented value without exception.
- [ ] Zero eligible stake is handled correctly.
- [ ] Zero eligible result count is handled correctly.
- [ ] Zero eligible odds count is handled correctly.

### Persistence

- [ ] Reads use Analytics-owned `analytics_bets`.
- [ ] Dashboard requests do not mutate `analytics_bets`.
- [ ] Dashboard requests do not mutate `processed_events`.
- [ ] Analytics does not access the Betting database.

### API

- [ ] API response matches `docs/api-contracts.md`.
- [ ] The response echoes all effective dashboard filters, including odds and stake ranges.
- [ ] Missing authenticated identity produces the documented unauthorized response.
- [ ] Invalid filters produce the documented client error.
- [ ] Empty datasets return the complete documented `200 OK` zero response.

## Boundary and negative cases

Tests must cover at least the documented variants of:

- no bets;
- only pending bets;
- zero eligible stake;
- negative profit;
- winning and losing bets;
- cashout;
- void;
- cancelled;
- mixed statuses;
- cross-user projections;
- single filter;
- multiple filters;
- filters with no matches;
- decimal values requiring documented rounding;
- equal `settledAt` values requiring the documented drawdown tie-breaker;
- recovery to a new peak and a non-zero current drawdown;
- valid zero-valued results.

Do not add unsupported domain behavior merely to increase edge-case count.

## Expected tests

This task uses the normal TDD workflow.

The blind test agent should create Red tests before production implementation.

The expected test layers are:

### Calculation/application tests

Protect:

- every metric formula;
- status eligibility;
- empty result;
- zero denominators;
- negative performance;
- decimal scale;
- rounding;
- maximum and current drawdown chronology;
- filter application semantics where application-owned.

Tests should deliberately use values capable of detecting:

- integer division;
- double/float conversion;
- premature rounding;
- wrong status denominator;
- cross-user contamination.

### Persistence integration tests

Use the project's real persistence strategy.

Protect:

- authenticated-user scoping;
- documented filters;
- mixed users;
- mixed statuses;
- decimal round-trip;
- empty queries.

If PostgreSQL-specific behavior matters, use PostgreSQL/Testcontainers consistently with the existing Analytics strategy.

Do not replace real persistence coverage with H2 when PostgreSQL semantics are relevant.

### API integration tests

Protect:

- authenticated request;
- expected response structure;
- documented metric values;
- filters;
- empty result;
- unauthorized request;
- invalid filters;
- user isolation.

Do not require frontend integration.

## Test quality constraints

Tests must protect behavior rather than freeze unnecessary implementation details.

Do not require without contractual reason:

- exact application class names;
- exact repository class names;
- JPA versus JDBC;
- exact SQL text;
- private methods;
- internal DTO naming;
- exact controller method names.

Architecture tests may protect layer boundaries where necessary.

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

Task 7.1 is complete only when:

- the specification is satisfied;
- Red tests were created and human-approved;
- implementation makes the approved tests Green;
- historical Analytics behavior remains Green;
- service regressions remain Green;
- independent final QA approves;
- the human approves the QA outcome;
- status reaches `DONE` through the documented state machine.

## Status and evidence

| Field | Value |
| --- | --- |
| Status | `DONE` |
| Red tests | `compileTestJava` Green. Two focused Task 7.1 runs each executed 32 tests: 28 expected Reds (24 HTTP `404` because `GET /analytics/dashboard` is absent; 4 because no dashboard application boundary exists) and 4 architecture boundaries Green. |
| Human test approval | Approved on 2026-08-19 after the test-only JSON decimal parsing and application-seam constructor corrections. |
| Implementation | Green — 32/32 protected Task 7.1 tests passed twice; required regressions passed. |
| Human implementation approval | Approved on 2026-08-20. |
| Final QA | Approved by human on 2026-08-20 |
| Evidence | Baseline Analytics compile/test/check, messaging-contract, and Betting check Green. Historical Analytics excluding Task71, messaging-contract, and Betting regressions Green. No production, migration, historical test, commit, staging, push, or merge change. |
