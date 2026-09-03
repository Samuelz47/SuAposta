# 8.3 — Implement betting and dashboard flows

## Context

Betting and Analytics APIs are consumed only through the API Gateway.

Authentication/session behavior already exists from Task 8.2.

The backend already provides:

- bet listing and creation;
- bet settlement;
- dashboard summary;
- bankroll evolution;
- performance breakdowns.

The frontend must present those backend projections without recreating domain
business rules in the browser.

## Objective

Expose the first authenticated betting-management and analytics experience:

- list the authenticated user's bets;
- create a bet;
- settle a pending bet;
- display dashboard summary metrics;
- display bankroll evolution;
- display grouped performance breakdowns.

## Protected routes

The primary authenticated routes are:

- `/dashboard`
- `/bets`

They remain protected by the authentication/session behavior from Task 8.2.

Unauthorized session behavior must reuse Task 8.2 infrastructure rather than be
reimplemented independently.

## API boundary

All Betting and Analytics traffic must use the centralized Gateway HTTP boundary
from Task 8.1.

Feature services must not directly target:

- betting-service;
- analytics-service;
- auth-service;
- internal service ports.

JWT handling remains centralized in the interceptor from Task 8.2.

Betting/dashboard feature services must not manually attach authentication
headers.

## Backend authority

The frontend must treat backend values as authoritative.

Do not recalculate server-side business state such as:

- bet profit;
- return amount;
- settlement result;
- dashboard ROI;
- dashboard yield;
- win rate;
- average odds;
- drawdown;
- bankroll cumulative profit;
- performance-breakdown metrics.

The frontend may only perform presentation formatting.

## Decimal presentation

Backend numeric values must be consumed as returned by the API.

The frontend must not introduce business rounding that changes the returned
value.

Display formatting may:

- add currency symbols;
- add `%`;
- format decimal separators according to the chosen UI locale.

Presentation formatting must not mutate the underlying API value.

## Status presentation

Bet statuses come from the backend contract.

The frontend must support the documented statuses:

- `PENDING`
- `WON`
- `LOST`
- `VOID`
- `CASHOUT`
- `CANCELLED`

Do not infer a different status from financial fields.

Unknown statuses must fail safely in presentation rather than crash the
application.

## Bets page

`/bets` must provide the initial authenticated bet-management flow.

At minimum it must support:

- loading the authenticated user's bet list;
- rendering an empty state;
- rendering an API error state;
- creating a new bet;
- settling an eligible pending bet.

The UI must not expose cross-user ownership controls.

No userId field/query control may allow choosing another user's data.

Ownership remains backend-derived from the authenticated Gateway session.

## Bet listing

The list must render the essential fields returned by the current Betting API
contract.

Do not require fields not present in that contract.

The list must support at least:

- initial loading;
- loaded data;
- empty collection;
- request failure.

No client-side request to Analytics is required to render the bet list.

### Listing API and pagination contract

The list uses:

`GET /bets`

The response wrapper contains:

- `content`
- `page`
- `size`
- `totalElements`
- `totalPages`

The documented defaults are `page = 0` and `size = 20`. Sorting is not part of
the contract, so the UI must not assume insertion order or date order.

Task 8.3 must implement basic pagination. The UI must provide previous-page and
next-page controls based on the response `page` and `totalPages` values. Do not
introduce infinite scroll, custom sorting, or advanced pagination.

## Bet creation

The create-bet form uses:

`POST /bets`

The client request contains:

- `sport`
- `league`
- `homeTeam`
- `awayTeam`
- `market`
- `selection`
- `odds`
- `stake`
- `placedAt`
- optional `notes`

`placedAt` is supplied by the client as an ISO-8601 Instant. The frontend must
not send `userId`, `status`, `profit`, `returnAmount`, `settledAt`, `createdAt`,
or `updatedAt`.

The frontend must validate fields required by the contract before submission.

Backend validation remains authoritative.

The frontend must not reproduce settlement/profit formulas.

A successful creation must make the newly created bet visible to the user
without requiring a full browser reload.

This may be achieved by either:

- updating local feature state with the returned bet;
- reloading the list from the Gateway.

Do not introduce global state management solely for this.

## Known backend prerequisite

Backend validation hardening for required create fields must be completed
before Task 8.3 implementation begins.

The prerequisite covers explicit HTTP/domain validation and confirmation of
requiredness for `sport`, `league`, `homeTeam`, `awayTeam`, `market`,
`selection`, and `placedAt`. It belongs to a separate backend task and is not
implemented by Task 8.3.

## Create-bet loading/error behavior

While creation is in progress:

- prevent duplicate submission;
- show a loading state.

On failure:

- preserve the page/session;
- show a safe user-facing error;
- do not display raw backend/infrastructure errors.

## Settlement

A settlement action must be offered only when the backend bet state allows the
documented settlement flow.

The settlement request uses:

`PATCH /bets/{id}/settle`

For `WON`, `LOST`, `VOID`, and `CANCELLED`, the request is:

```json
{
  "status": "..."
}
```

For `CASHOUT`, the request is:

```json
{
  "status": "CASHOUT",
  "returnAmount": 130.00
}
```

At minimum the UI must support the final statuses required by the current
settlement contract.

Do not invent client-side state transitions.

For `CASHOUT`, require only the presence of `returnAmount`. Do not impose a
positive `returnAmount` constraint as a frontend business rule because the
current backend contract does not define that restriction.

For statuses where the backend calculates the final financial values, do not ask
the client to calculate them.

## Settlement success

After successful settlement:

- update the visible bet with the authoritative API response, or
- reload the bet list.

The successful response is the complete `BetResponse`, including `notes`.
Financial response values remain authoritative from the backend.

Do not locally derive:

- profit;
- return amount;
- final status fields.

## Settlement failure

A failed settlement must:

- keep the current page usable;
- preserve the previous authoritative visible state;
- show a safe error;
- not optimistically mark the bet as settled.

Double settlement attempts must remain governed by backend behavior.

## Analytics consistency

Betting write responses are immediately authoritative for the Betting UI.

Analytics projections are eventually consistent through RabbitMQ.

After bet creation or settlement, Dashboard Summary, Bankroll Evolution and
Performance Breakdown may temporarily expose the previous projection state.

The frontend must NOT:

- calculate Analytics metrics locally;
- patch Analytics values from the Betting response;
- synthesize bankroll points;
- synthesize breakdown groups.

An immediate Analytics refresh returning the previous projection is valid and
must not be treated as a frontend error.

Analytics values become authoritative when returned by a later Analytics
request.

## Dashboard page

`/dashboard` must consume all initial Analytics read models provided by Phase 7:

1. Dashboard summary — Task 7.1
2. Bankroll evolution — Task 7.2
3. Performance breakdown — Task 7.3

The UI does not need an advanced design system.

The goal is a functional first analytics experience.

## Dashboard summary

Consume:

`GET /analytics/dashboard`

Present the authoritative summary returned by the API.

At minimum expose the key metrics returned by the current contract.

The UI must not recompute the metrics from the bet list.

The summary must handle:

- loading;
- populated response;
- empty/zero response;
- API error.

## Bankroll evolution

Consume:

`GET /analytics/bankroll-evolution`

Render the returned ordered points.

The frontend must not recompute:

- profit;
- cumulativeProfit;
- bankroll.

The API ordering is authoritative.

A simple visualization is acceptable.

Examples:

- lightweight line chart;
- accessible table/list;
- both.

Do not introduce an unnecessary visualization framework if Angular/native
rendering or the chosen existing dependency is sufficient.

If no chart dependency already exists, adding one requires explicit
justification and must remain scoped to this task.

The UI must handle:

- loading;
- populated points;
- empty points;
- API error.

## Performance breakdown

Consume:

`GET /analytics/performance/breakdown`

The UI must allow selecting one supported `groupBy` value:

- `SPORT`
- `LEAGUE`
- `TEAM`
- `MARKET`
- `MONTH`
- `WEEK`
- `DAY`

The request must send the exact backend enum value.

Do not lowercase/normalize the API groupBy value.

The UI must display the returned observed groups and metrics.

The frontend must not:

- regroup raw bets;
- calculate bucket metrics;
- synthesize missing groups;
- duplicate TEAM semantics locally.

The backend response is authoritative.

## Initial performance-breakdown selection

Use:

`SPORT`

as the initial/default groupBy shown by the dashboard.

Changing the selection must trigger a new Analytics request for the selected
grouping.

Do not calculate alternate groupings from an already received response.

## Analytics filters

Task 8.3 may expose only filters already supported by the corresponding backend
endpoint.

Do not invent client-only filtering semantics that conflict with the API.

For this initial UI, filters are optional unless explicitly required below.

Minimum required behavior:

- dashboard summary may load without optional filters;
- bankroll evolution may load without optional filters;
- performance breakdown may load with only required `groupBy=SPORT`.

Advanced analytics filtering UI is not required for this task.

If filters are implemented, requests must use the exact backend contracts.

## Dashboard request independence

Summary, bankroll evolution, and performance breakdown are independent backend
read models.

One request failure must not require the entire dashboard page to crash.

The page should be able to represent per-section loading/error/empty states.

Do not combine all three backend contracts into one synthetic frontend API
request.

## Loading behavior

Each essential flow must expose explicit loading state:

- bet list;
- bet creation;
- bet settlement;
- dashboard summary;
- bankroll evolution;
- performance breakdown.

Avoid duplicate submissions/requests caused by a single user action.

## Safe API errors

Errors presented in the UI must not expose:

- stack traces;
- SQL;
- Java exception classes;
- internal hostnames;
- internal ports;
- service implementation details.

Reuse common safe error handling where appropriate.

## Unauthorized behavior

A `401` from Betting or Analytics must use the session invalidation behavior
defined in Task 8.2:

- clear session;
- redirect to `/login`.

Do not duplicate competing authentication logic inside feature components.

## Empty states

Provide explicit user-visible empty states for:

- no bets;
- empty dashboard/zero summary;
- no bankroll points;
- no performance-breakdown items.

Empty data must not be treated as an API failure.

## Acceptance criteria

### Betting

- [ ] Authenticated user can list own bets through the Gateway.
- [ ] Bet list exposes loading, empty, success, and error states.
- [ ] Bet list uses the documented `content`, `page`, `size`, `totalElements`,
  and `totalPages` wrapper.
- [ ] Previous-page and next-page controls use `page` and `totalPages`.
- [ ] Bet-list presentation does not assume insertion or date ordering.
- [ ] Authenticated user can create a bet using the documented API contract.
- [ ] Required creation fields are validated before submission.
- [ ] Bet creation prevents duplicate submission while loading.
- [ ] Successful creation updates or reloads the authoritative bet list.
- [ ] Authenticated user can settle an eligible pending bet.
- [ ] Settlement uses the documented Task 5.3 contract.
- [ ] CASHOUT collects only the client input explicitly required by the
  backend contract.
- [ ] CASHOUT requires `returnAmount` presence without adding a frontend
  positivity business rule.
- [ ] Successful settlement uses authoritative returned/reloaded values.
- [ ] Settlement response preserves the authoritative `notes` value.
- [ ] Failed settlement does not optimistically mutate the bet into a final
  state.
- [ ] No cross-user ownership selector exists.

### Dashboard summary

- [ ] `/dashboard` consumes `GET /analytics/dashboard`.
- [ ] Returned metrics are presented without client-side business
  recalculation.
- [ ] Summary loading, zero/empty, success, and error states are represented.

### Bankroll evolution

- [ ] Dashboard consumes `GET /analytics/bankroll-evolution`.
- [ ] Returned point order is preserved.
- [ ] `profit`, `cumulativeProfit`, and `bankroll` are not recalculated in the
  client.
- [ ] Loading, empty, success, and error states are represented.
- [ ] The evolution is presented in a readable visual or tabular form.

### Performance breakdown

- [ ] Dashboard consumes `GET /analytics/performance/breakdown`.
- [ ] Default `groupBy` is `SPORT`.
- [ ] User can select each supported groupBy value.
- [ ] Selecting a groupBy performs a Gateway request using the exact enum value.
- [ ] Returned groups/metrics are rendered without client-side regrouping or
  metric recalculation.
- [ ] No synthetic zero groups are generated.
- [ ] Loading, empty, success, and error states are represented.

### Boundaries

- [ ] Betting and Analytics requests target only the Gateway.
- [ ] Feature services do not manually attach JWT headers.
- [ ] `401` behavior reuses Task 8.2 session handling.
- [ ] No backend business formula is duplicated in frontend production code.
- [ ] An immediately stale Analytics projection after a Betting write is not
  treated as a frontend error or patched from the Betting response.
- [ ] No global state-management dependency is introduced solely for these
  flows.

## Boundary and negative cases

- [ ] Bet-list API error.
- [ ] Empty bet list.
- [ ] Previous/next pagination boundaries.
- [ ] Invalid create-bet form input.
- [ ] Create-bet API failure.
- [ ] Duplicate create submission while loading.
- [ ] Invalid settlement input.
- [ ] Settlement API failure.
- [ ] Attempt to settle a non-eligible bet is not represented as an allowed
  optimistic UI transition.
- [ ] Dashboard summary API failure.
- [ ] Dashboard zero/empty state.
- [ ] Empty bankroll evolution.
- [ ] Bankroll evolution API failure.
- [ ] Empty performance breakdown.
- [ ] Performance breakdown API failure.
- [ ] Changing groupBy uses backend request rather than local regrouping.
- [ ] An immediate Analytics refresh may show a previous projection without
  being handled as an error.
- [ ] Unknown backend status does not crash the page.
- [ ] Unauthorized Betting/Analytics request invalidates the session using the
  Task 8.2 behavior.
- [ ] No direct internal-service URL exists in feature production code.

## Out of scope

- Advanced design system.
- Full responsive-design polish.
- Offline mode.
- Websocket/live updates.
- Bet editing unless explicitly required by the current roadmap.
- Bet deletion unless explicitly required by the current roadmap.
- Advanced analytics filter panels.
- User-configurable dashboard layouts.
- Export/download.
- Infinite scroll.
- Custom sorting.
- Advanced pagination.
- Client-side analytics engine.
- Client-side aggregation of raw bets.
- Redis/cache.
- Global state-management library.
- Deposits.
- Withdrawals.
- Wallet/ledger.
- Initial-bankroll management.
- Notifications.
- Advanced charting interactions.

## Dependencies

- Tasks 5.2 and 5.3.
- Tasks 7.1, 7.2, and 7.3.
- Tasks 8.1 and 8.2.
- Gateway Betting and Analytics route contracts.

## Expected tests

Focused Angular tests must cover at least:

### Betting

- bet-list API service;
- bet-list loading/success/empty/error states;
- pagination request parameters and previous/next boundary behavior;
- no assumed insertion/date ordering;
- create-bet form validation;
- create-bet request payload;
- duplicate-submit prevention;
- successful creation refresh/update behavior;
- settlement eligibility presentation;
- settlement request payload;
- CASHOUT-specific required client input;
- absence of a frontend CASHOUT positivity business rule;
- successful settlement refresh/update behavior;
- settlement response handling including `notes`;
- settlement failure preserving previous visible state;
- Gateway-only HTTP targeting.

### Dashboard

- summary API service;
- summary rendering;
- summary zero/empty/error behavior;
- bankroll-evolution API service;
- bankroll point rendering/order preservation;
- bankroll empty/error behavior;
- performance-breakdown API service;
- default `SPORT` grouping;
- supported groupBy selection;
- exact groupBy request value;
- breakdown rendering;
- breakdown empty/error behavior;
- no local regrouping/business-metric calculation.
- immediate stale Analytics projection handled as an eventually consistent,
  non-error response.

### Authentication integration

- protected feature behavior with a valid session;
- Betting/Analytics `401` delegating to Task 8.2 session invalidation;
- no manual Authorization header logic in feature services.

## Required verification

The task must successfully execute:

- frontend unit tests;
- production build;
- documented local serve flow.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
