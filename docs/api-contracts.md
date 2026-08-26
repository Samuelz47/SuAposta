````markdown
# API Contracts

## 1. Overview

This document defines the initial REST API contracts for Bet Control SaaS.

The frontend should call the API Gateway.

Internal services should not be called directly by the frontend.

External flow:

```text
Angular App
  ↓
API Gateway
  ↓
Auth Service / Betting Service / Analytics Service
```

Base URL for local development:

```text
http://localhost:8080
```

The API Gateway should route requests to internal services.

---

## 2. General API Rules

## 2.1 Content Type

Requests and responses should use JSON.

```http
Content-Type: application/json
```

---

## 2.2 Authentication

Protected endpoints require:

```http
Authorization: Bearer <jwt>
```

Public endpoints:

```text
POST /auth/register
POST /auth/login
```

Protected endpoints:

```text
GET /auth/me
POST /bets
GET /bets
GET /bets/{id}
PUT /bets/{id}
PATCH /bets/{id}/settle
GET /analytics/dashboard
GET /analytics/bets/performance
```

### JWT access token contract

Access tokens issued by the Auth Service and validated by the API Gateway must follow this initial contract:

- JWT signing algorithm: `HS256`.
- The signing secret must be provided through configuration/environment variables.
- The signing secret must never be hardcoded in production code or committed to the repository.
- The Auth Service and API Gateway must use the same signing contract.
- Required claims:
  - `sub`: authenticated user identifier.
  - `iat`: token issuance timestamp.
  - `exp`: token expiration timestamp.
- `sub` must contain the stable UUID of the authenticated user.
- Passwords, password hashes, credentials, or other sensitive authentication data must never be included in JWT claims.
- Tokens with an invalid signature, invalid format, or expired `exp` must be rejected with `401 Unauthorized`.

### Gateway authenticated identity contract

After successfully validating a JWT on a protected route:

- The API Gateway must use the `sub` claim as the authenticated user identity.
- The Gateway must propagate only the required authenticated identity to downstream services using:

```http
X-User-Id: <authenticated-user-uuid>
```

- The original `Authorization` header containing the Bearer JWT must be removed before forwarding the request downstream.
- JWT claims that are not part of the documented downstream identity contract must not be converted into internal headers.
- Authentication at the Gateway establishes authenticated identity only.
- Resource ownership and service-level authorization must be enforced by the responsible downstream service.

Requests to protected endpoints with missing, malformed, expired, or invalid JWTs must return:

```text
401 Unauthorized
```

---

## 2.3 IDs

Use UUID for entity identifiers.

Example:

```json
{
  "id": "f8c6eb32-54d4-4024-9581-7a0a8d6f4f19"
}
```

---

## 2.4 Dates

Use ISO-8601 date/time format.

Example:

```text
2026-07-21T22:00:00Z
```

---

## 2.5 Money and Decimal Values

Use decimal numbers in JSON.

Examples:

```json
{
  "stake": 100.00,
  "odds": 2.10,
  "profit": 110.00,
  "roi": 12.50
}
```

Backend implementation must use `BigDecimal`.

---

## 2.6 Error Response

Standard error response:

```json
{
  "timestamp": "2026-07-21T22:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Stake must be greater than zero",
  "path": "/bets"
}
```

Validation error response:

```json
{
  "timestamp": "2026-07-21T22:00:00Z",
  "status": 400,
  "error": "Validation Error",
  "message": "Invalid request fields",
  "path": "/bets",
  "fieldErrors": [
    {
      "field": "stake",
      "message": "must be greater than 0"
    }
  ]
}
```

---

## 3. Auth API

## 3.1 Register User

```http
POST /auth/register
```

### Request

```json
{
  "name": "Samuel Gomes",
  "email": "samuel@example.com",
  "password": "StrongPassword123"
}
```

### Response `201 Created`

```json
{
  "id": "b40da580-a017-4a11-bd42-c67aa6409166",
  "name": "Samuel Gomes",
  "email": "samuel@example.com",
  "createdAt": "2026-07-21T21:00:00Z"
}
```

### Possible errors

```text
400 Bad Request
409 Conflict
```

Conflict example:

```json
{
  "timestamp": "2026-07-21T21:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Email already registered",
  "path": "/auth/register"
}
```

---

## 3.2 Login

```http
POST /auth/login
```

### Request

```json
{
  "email": "samuel@example.com",
  "password": "StrongPassword123"
}
```

### Response `200 OK`

```json
{
  "accessToken": "jwt-token",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": "b40da580-a017-4a11-bd42-c67aa6409166",
    "name": "Samuel Gomes",
    "email": "samuel@example.com"
  }
}
```

### Possible errors

```text
400 Bad Request
401 Unauthorized
```

---

## 3.3 Current User

```http
GET /auth/me
```

### Response `200 OK`

```json
{
  "id": "b40da580-a017-4a11-bd42-c67aa6409166",
  "name": "Samuel Gomes",
  "email": "samuel@example.com"
}
```

### Possible errors

```text
401 Unauthorized
```

---

## 4. Betting API

## 4.1 Create Bet

```http
POST /bets
```

### Request

```json
{
  "sport": "FOOTBALL",
  "league": "Brasileirão Série A",
  "homeTeam": "Fortaleza",
  "awayTeam": "Bahia",
  "market": "MATCH_RESULT",
  "selection": "Fortaleza",
  "odds": 2.10,
  "stake": 100.00,
  "placedAt": "2026-07-21T20:30:00Z",
  "notes": "Home win based on recent form"
}
```

### Response `201 Created`

```json
{
  "id": "f8c6eb32-54d4-4024-9581-7a0a8d6f4f19",
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
  "profit": null,
  "returnAmount": null,
  "placedAt": "2026-07-21T20:30:00Z",
  "settledAt": null,
  "notes": "Home win based on recent form",
  "createdAt": "2026-07-21T21:00:00Z",
  "updatedAt": "2026-07-21T21:00:00Z"
}
```

### Rules

- Requires authentication.
- `userId` must come from the authenticated user, not from the request body.
- `stake` must be greater than zero.
- `odds` must be greater than one.
- Initial status must be `PENDING`.
- Event publishing is outside the current Bet creation contract. When event publishing is introduced in the event phase, a successfully persisted Bet creation must publish `BET_CREATED`.

### Possible errors

```text
400 Bad Request
401 Unauthorized
```

---

## 4.2 List Bets

```http
GET /bets
```

### Query parameters

```text
startDate
endDate
sport
league
team
market
status
minOdds
maxOdds
minStake
maxStake
page
size
```

Pagination parameters are optional.

Defaults:

```text
page = 0
size = 20
```

Rules:

- `page` is zero-based and must be greater than or equal to `0`.
- `size` must be greater than `0`.
- When `page` is omitted, use `0`.
- When `size` is omitted, use `20`.
- The response must return the resolved `page` and `size` values.
- Sorting is not part of the current `GET /bets` contract. A `sort` parameter may be introduced later only with an explicitly documented syntax and behavior.

Example:

```http
GET /bets?sport=FOOTBALL&league=Brasileirão%20Série%20A&status=PENDING&page=0&size=20
```

### Response `200 OK`

```json
{
  "content": [
    {
      "id": "f8c6eb32-54d4-4024-9581-7a0a8d6f4f19",
      "sport": "FOOTBALL",
      "league": "Brasileirão Série A",
      "homeTeam": "Fortaleza",
      "awayTeam": "Bahia",
      "market": "MATCH_RESULT",
      "selection": "Fortaleza",
      "odds": 2.10,
      "stake": 100.00,
      "status": "PENDING",
      "profit": null,
      "returnAmount": null,
      "placedAt": "2026-07-21T20:30:00Z",
      "settledAt": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### Rules

- Requires authentication.
- Must return only bets from the authenticated user.
- Pagination must be supported using the documented `page` and `size` defaults.
- Filtering should be optional.

---

## 4.3 Get Bet By ID

```http
GET /bets/{id}
```

### Response `200 OK`

```json
{
  "id": "f8c6eb32-54d4-4024-9581-7a0a8d6f4f19",
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
  "profit": null,
  "returnAmount": null,
  "placedAt": "2026-07-21T20:30:00Z",
  "settledAt": null,
  "notes": "Home win based on recent form",
  "createdAt": "2026-07-21T21:00:00Z",
  "updatedAt": "2026-07-21T21:00:00Z"
}
```

### Possible errors

```text
401 Unauthorized
404 Not Found
```

---

## 4.4 Update Bet

```http
PUT /bets/{id}
```

### Request

```json
{
  "sport": "FOOTBALL",
  "league": "Brasileirão Série A",
  "homeTeam": "Fortaleza",
  "awayTeam": "Bahia",
  "market": "MATCH_RESULT",
  "selection": "Fortaleza",
  "odds": 2.25,
  "stake": 120.00,
  "placedAt": "2026-07-21T20:30:00Z",
  "notes": "Updated stake and odds"
}
```

### Response `200 OK`

```json
{
  "id": "f8c6eb32-54d4-4024-9581-7a0a8d6f4f19",
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
  "profit": null,
  "returnAmount": null,
  "placedAt": "2026-07-21T20:30:00Z",
  "settledAt": null,
  "notes": "Updated stake and odds",
  "createdAt": "2026-07-21T21:00:00Z",
  "updatedAt": "2026-07-21T21:20:00Z"
}
```

### Rules

- Requires authentication.
- Must update only bets from authenticated user.
- Only `PENDING` bets can be updated in the first version.
- Event publishing is outside the current Bet update contract. When event publishing is introduced in the event phase, a successfully persisted Bet update must publish `BET_UPDATED`.

### Possible errors

```text
400 Bad Request
401 Unauthorized
404 Not Found
409 Conflict
```

---

## 4.5 Settle Bet

```http
PATCH /bets/{id}/settle
```

### Request for WON

```json
{
  "status": "WON"
}
```

### Request for LOST

```json
{
  "status": "LOST"
}
```

### Request for VOID

```json
{
  "status": "VOID"
}
```

### Request for CANCELLED

```json
{
  "status": "CANCELLED"
}
```

### Request for CASHOUT

```json
{
  "status": "CASHOUT",
  "returnAmount": 130.00
}
```

### Response `200 OK`

```json
{
  "id": "f8c6eb32-54d4-4024-9581-7a0a8d6f4f19",
  "userId": "b40da580-a017-4a11-bd42-c67aa6409166",
  "sport": "FOOTBALL",
  "league": "Brasileirão Série A",
  "homeTeam": "Fortaleza",
  "awayTeam": "Bahia",
  "market": "MATCH_RESULT",
  "selection": "Fortaleza",
  "odds": 2.10,
  "stake": 100.00,
  "status": "WON",
  "profit": 110.00,
  "returnAmount": 210.00,
  "placedAt": "2026-07-21T20:30:00Z",
  "settledAt": "2026-07-21T22:00:00Z",
  "createdAt": "2026-07-21T21:00:00Z",
  "updatedAt": "2026-07-21T22:10:00Z"
}
```

### Rules

- Requires authentication.
- Must settle only bets from authenticated user.
- Only `PENDING` bets can be settled in the first version.
- Status must be one of:

```text
WON
LOST
VOID
CASHOUT
CANCELLED
```

- `settledAt` is generated by the Betting Service after a successful settlement.
- The client must not provide or control `settledAt`.
- `updatedAt` is generated by the Betting Service after a successful settlement.
- `createdAt` must remain unchanged.
- For `WON`, profit and return amount are calculated automatically.
- For `LOST`, profit and return amount are calculated automatically.
- For `VOID`, profit is zero and return amount equals the stake.
- For `CANCELLED`, profit is zero and return amount equals the stake.
- For `CASHOUT`, `returnAmount` must be provided by the client and profit is calculated as `returnAmount - stake`.
- The client must not provide or control `profit`.
- Event publishing is outside the current Bet settlement contract. When event publishing is introduced in the event phase, a successfully persisted Bet settlement must publish `BET_SETTLED`.

### Possible errors

```text
400 Bad Request
401 Unauthorized
404 Not Found
409 Conflict
```

---

## 5. Analytics API

## 5.1 Dashboard Metrics

```http
GET /analytics/dashboard
```

### Query parameters

```text
startDate
endDate
sport
league
team
market
status
minOdds
maxOdds
minStake
maxStake
```

All parameters are optional. Every supplied parameter is combined with the authenticated-user predicate and every other supplied parameter using logical `AND`.

Filter contract:

| Parameter | Semantics |
| --- | --- |
| `startDate` | `placedAt >= startDate` |
| `endDate` | `placedAt <= endDate` |
| `sport` | Exact, case-sensitive equality with `sport` |
| `league` | Exact, case-sensitive equality with `league` |
| `team` | Exact, case-sensitive equality with either `homeTeam` or `awayTeam` |
| `market` | Exact, case-sensitive equality with `market` |
| `status` | Exact enum value: `PENDING`, `WON`, `LOST`, `VOID`, `CASHOUT`, or `CANCELLED` |
| `minOdds` | `odds >= minOdds` |
| `maxOdds` | `odds <= maxOdds` |
| `minStake` | `stake >= minStake` |
| `maxStake` | `stake <= maxStake` |

Date limits are inclusive and are parsed as ISO-8601 instants. Offset-bearing values represent their equivalent instant. When both are supplied, `startDate` must be less than or equal to `endDate`.

Text filters must be non-blank and are not trimmed, normalized, partially matched, or matched case-insensitively by the server. `team` never matches `selection`.

Odds filters use the domain odds scale (`4`, `HALF_UP`) and must normalize to a value strictly greater than `1.0000`. Stake filters use the money scale (`2`, `HALF_UP`) and must normalize to a value strictly greater than `0.00`. After normalization, each minimum must be less than or equal to its corresponding maximum.

Malformed dates or decimals, blank text values, unsupported statuses, invalid ranges, and values outside those constraints return `400 Bad Request` using the standard validation error response.

Example:

```http
GET /analytics/dashboard?startDate=2026-07-01T00:00:00Z&endDate=2026-07-31T23:59:59Z&sport=FOOTBALL
```

### Response `200 OK`

```json
{
  "summary": {
    "totalStake": 1000.00,
    "totalProfit": 120.00,
    "roi": 12.00,
    "yield": 12.00,
    "winRate": 55.00,
    "averageOdds": 2.0500,
    "betsCount": 20,
    "wonBets": 11,
    "lostBets": 9,
    "voidBets": 0,
    "cashoutBets": 0,
    "cancelledBets": 0,
    "maxDrawdown": 180.00,
    "currentDrawdown": 40.00
  },
  "filters": {
    "startDate": "2026-07-01T00:00:00Z",
    "endDate": "2026-07-31T23:59:59Z",
    "sport": "FOOTBALL",
    "league": null,
    "team": null,
    "market": null,
    "status": null,
    "minOdds": null,
    "maxOdds": null,
    "minStake": null,
    "maxStake": null
  }
}
```

The formatting above is illustrative JSON; the `filters` object contains all eleven filter fields in this order-independent shape:

```text
startDate, endDate, sport, league, team, market, status,
minOdds, maxOdds, minStake, maxStake
```

It echoes the effective filter values. Missing filters are `null`; dates are serialized as ISO-8601 instants; odds and stake filters are serialized at their normalized domain scales.

When no projection matches, the complete response is:

```json
{
  "summary": {
    "totalStake": 0.00,
    "totalProfit": 0.00,
    "roi": 0.00,
    "yield": 0.00,
    "winRate": 0.00,
    "averageOdds": 0.0000,
    "betsCount": 0,
    "wonBets": 0,
    "lostBets": 0,
    "voidBets": 0,
    "cashoutBets": 0,
    "cancelledBets": 0,
    "maxDrawdown": 0.00,
    "currentDrawdown": 0.00
  },
  "filters": {
    "startDate": null,
    "endDate": null,
    "sport": null,
    "league": null,
    "team": null,
    "market": null,
    "status": null,
    "minOdds": null,
    "maxOdds": null,
    "minStake": null,
    "maxStake": null
  }
}
```

If valid filters were supplied but matched no projection, their effective values replace the corresponding `null` values in this otherwise identical response.

### Rules

- Requires authentication.
- Must return only data from authenticated user.
- Must read from Analytics Service projection tables.
- Must not query Betting Service database directly.
- Metric formulas, status eligibility, scales, rounding, zero denominators, and drawdown ordering follow the dashboard summary aggregation contract in `docs/domain.md`.
- A `status` filter narrows the source rows but never changes a metric's eligibility set. For example, `status=PENDING` can produce a non-zero `betsCount` while all financial, percentage, average-odds, and drawdown metrics remain zero.
- Missing or malformed trusted `X-User-Id` at the Analytics boundary returns `401 Unauthorized`; ownership must never be accepted from a query parameter or request body.
- If no projection matches the authenticated user and filters, return `200 OK` with every summary numeric value zero at its documented scale, every count zero, and the effective `filters` object.
- `summary` always contains exactly: `totalStake`, `totalProfit`, `roi`, `yield`, `winRate`, `averageOdds`, `betsCount`, `wonBets`, `lostBets`, `voidBets`, `cashoutBets`, `cancelledBets`, `maxDrawdown`, and `currentDrawdown`.

---

## 5.2 Bankroll Evolution

```http
GET /analytics/bankroll-evolution
```

### Query parameters

```text
startDate
endDate
sport
league
team
market
```

### Response `200 OK`

```json
{
  "points": [
    {
      "date": "2026-07-01",
      "profit": 50.00,
      "cumulativeProfit": 50.00,
      "bankroll": 50.00
    },
    {
      "date": "2026-07-02",
      "profit": -100.00,
      "cumulativeProfit": -50.00,
      "bankroll": -50.00
    }
  ]
}
```

### Rules

- Requires authentication.
- Must return only projections belonging to the authenticated user.
- All supplied query parameters are optional and compose with logical `AND`.
- `startDate` filters with `settledAt >= startDate`.
- `endDate` filters with `settledAt <= endDate`.
- `startDate` and `endDate` are inclusive and must be parsed as ISO-8601 instants. Offset-bearing values representing the same instant are equivalent.
- Malformed date values return `400 Bad Request` using the standard validation error response.
- When both date filters are supplied, `startDate` greater than `endDate` returns `400 Bad Request`.
- Date and dimension filters are applied before status eligibility, ordering, and cumulative calculation. A filtered series starts its cumulative calculation at zero and must not include profit from excluded projections.
- Dimension filters use the same exact matching semantics as the Analytics dashboard: `sport`, `league`, and `market` are exact case-sensitive matches; `team` matches exactly either `homeTeam` or `awayTeam`.
- Only `WON`, `LOST`, and `CASHOUT` projections are eligible.
- `PENDING`, `VOID`, and `CANCELLED` projections are excluded and do not create points or affect cumulative values.
- Each eligible bet generates exactly one point. Points are not grouped by date or any other period; multiple eligible bets may therefore have the same public `date`.
- Points are ordered by `settledAt ASC`, then `betId ASC`. The `betId` tie-breaker is mandatory when settlement timestamps are equal; database row order must not be used.
- Each point's `profit` is the persisted Analytics projection value. Analytics must not recalculate settlement profit.
- The internal baseline is `0.00`; no artificial initial zero point is returned.
- `cumulativeProfit` is the sequential sum of eligible projected profits after filtering.
- `bankroll` is equal to `cumulativeProfit` in this MVP. It is cumulative performance from a zero baseline, not a real account balance.
- Negative cumulative values are valid, and an eligible zero-profit bet still produces a point.
- If no eligible projections match, return `200 OK` with `{"points": []}`.

---

## 5.3 Performance Breakdown

```http
GET /analytics/performance/breakdown
```

### Query parameters

```text
groupBy
startDate
endDate
sport
league
market
```

Allowed `groupBy` values:

```text
SPORT
LEAGUE
TEAM
MARKET
MONTH
WEEK
DAY
```

Example:

```http
GET /analytics/performance/breakdown?groupBy=LEAGUE&sport=FOOTBALL
```

### Response `200 OK`

```json
{
  "groupBy": "LEAGUE",
  "items": [
    {
      "name": "Brasileirão Série A",
      "betsCount": 10,
      "totalStake": 500.00,
      "totalProfit": 80.00,
      "roi": 16.00,
      "yield": 16.00,
      "winRate": 60.00
    },
    {
      "name": "Premier League",
      "betsCount": 8,
      "totalStake": 400.00,
      "totalProfit": -20.00,
      "roi": -5.00,
      "yield": -5.00,
      "winRate": 37.50
    }
  ]
}
```

### Rules

- Requires authentication.
- Must return only authenticated user data.
- Must support grouping by common dashboard dimensions.
- If `groupBy` is invalid, return `400 Bad Request`.

---

## 6. Initial Frontend Routes

The Angular frontend should initially support:

```text
/login
/register
/dashboard
/bets
/bets/new
/bets/:id
/settings
```

---

## 7. API Gateway Routing

Initial route mapping:

```text
/auth/**       -> auth-service
/bets/**       -> betting-service
/analytics/**  -> analytics-service
```

Local suggested ports:

```text
api-gateway:       8080
auth-service:      8081
betting-service:   8082
analytics-service: 8083
```

The frontend should call only:

```text
http://localhost:8080
```

---

## 8. Contract Change Rules

When changing an API contract:

- Update this file.
- Update affected request/response DTOs.
- Update frontend services if needed.
- Update tests.
- Avoid breaking changes unless explicitly requested.

Breaking changes include:

- Renaming fields.
- Removing fields.
- Changing field types.
- Changing endpoint paths.
- Changing authentication requirements.
- Changing status behavior.
````
