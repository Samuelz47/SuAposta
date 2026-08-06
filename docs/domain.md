# Domain

## 1. Overview

This document defines the core business domain for Bet Control SaaS.

The platform helps users register sports bets, manage their bankroll, and analyze performance through metrics such as profit, ROI, yield, win rate, and drawdown.

The first version should keep the domain simple and focused. The goal is to build a clear foundation before adding advanced features such as sportsbook integrations, automatic odds import, catalogs, subscriptions, or AI recommendations.

---

## 2. Core Domain Concepts

## 2.1 User

A user is a person who uses the platform to manage bets and analyze performance.

The Auth Service owns user identity.

Initial user data:

```text
id
name
email
passwordHash
role
createdAt
updatedAt
```

Rules:

- Email must be unique.
- Password must be stored as a hash.
- Password must never be exposed through APIs or events.
- Other services should reference users by `userId`.

---

## 2.2 Bankroll

Bankroll is the amount of money the user uses as betting capital.

The first version may calculate bankroll evolution from settled bets.

Initial approach:

```text
currentBankroll = initialBankroll + totalProfit
```

The first MVP may store `initialBankroll` in user settings or analytics settings later.

For the first implementation, bankroll can be simplified and calculated mainly through profit history.

Future improvements:

- Bankroll deposits.
- Bankroll withdrawals.
- Multiple bankrolls.
- Bankroll reset.
- Bankroll history table.
- Bankroll by bookmaker.

---

## 2.3 Bet

A bet is a sports betting operation registered by the user.

The Betting Service owns the bet lifecycle.

Initial bet fields:

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

Rules:

- A bet must belong to one user.
- A bet must have positive stake.
- A bet must have odds greater than 1.
- A bet must start as `PENDING`.
- A pending bet may be updated.
- A pending bet may be settled.
- A settled bet should not be settled again unless a correction flow is explicitly created.
- Pending bets should not affect profit, ROI, yield, or drawdown calculations.
- Settled bets should affect analytics according to their final status.

---

## 2.4 Sport

Sport represents the sport related to the bet.

Examples:

```text
FOOTBALL
BASKETBALL
TENNIS
VOLLEYBALL
BASEBALL
MMA
OTHER
```

Initial recommendation:

Use an enum or string in the Betting Service.

Do not create a dedicated Sport Catalog Service in the first version.

---

## 2.5 League

League represents the competition related to the bet.

Examples:

```text
Brasileirão Série A
Premier League
Champions League
NBA
ATP Wimbledon
UFC
```

Initial recommendation:

Use a string.

Do not normalize leagues into a separate table in the first version unless explicitly requested.

---

## 2.6 Team

Team represents one of the teams or participants involved in the bet.

Initial fields on Bet:

```text
homeTeam
awayTeam
```

Rules:

- Team fields may be optional for sports where the concept does not apply.
- For tennis, MMA, or individual sports, `homeTeam` and `awayTeam` may represent participants.
- Team normalization is out of scope for the first version.

---

## 2.7 Market

Market represents the type of bet.

Examples:

```text
MATCH_RESULT
OVER_UNDER
BOTH_TEAMS_TO_SCORE
HANDICAP
DOUBLE_CHANCE
CORRECT_SCORE
PLAYER_PROPS
OTHER
```

Initial recommendation:

Use an enum or string.

Keep the implementation flexible enough to support new markets later.

---

## 2.8 Selection

Selection is the specific outcome chosen by the user.

Examples:

```text
Fortaleza
Over 2.5
Both teams to score: Yes
Home -1.5
Draw
```

Rules:

- Selection should be stored as text in the first version.
- The system should not validate whether the selection exists in an external sportsbook.
- External sportsbook validation is out of scope.

---

## 2.9 Odds

Odds represent the multiplier used to calculate the return of a won bet.

Rules:

- Odds must be greater than 1.
- Odds must use decimal format.
- Odds must be stored with `BigDecimal`.
- Do not use `double` or `float`.

Example:

```text
2.10
1.85
3.50
```

---

## 2.10 Stake

Stake is the amount of money risked in the bet.

Rules:

- Stake must be greater than 0.
- Stake must use `BigDecimal`.
- Do not use `double` or `float`.

Example:

```text
100.00
25.50
10.00
```

---

## 2.11 Profit

Profit is the net financial result of a bet.

Rules:

- Profit is null while a bet is pending.
- Won bets generate positive profit.
- Lost bets generate negative profit.
- Void bets generate zero profit.
- Cancelled bets generate zero profit.
- Cashout bets use custom profit informed by the user.

---

## 2.12 Return Amount

Return amount is the total amount returned to the user after settlement.

Rules:

- Won bet return amount is `stake * odds`.
- Lost bet return amount is `0`.
- Void bet return amount is `stake`.
- Cancelled bet return amount is `stake`.
- Cashout return amount is `stake + profit`.

---

## 3. Bet Status

Initial statuses:

```text
PENDING
WON
LOST
VOID
CASHOUT
CANCELLED
```

## 3.1 PENDING

The bet was created but has not been resolved.

Rules:

- Can be updated.
- Can be settled.
- Should not count toward profit metrics.
- Should not count toward ROI.
- Should not count toward yield.
- Should not count toward win rate.
- Should not count toward drawdown.

---

## 3.2 WON

The bet was successful.

Rules:

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

---

## 3.3 LOST

The bet was unsuccessful.

Rules:

```text
profit = -stake
returnAmount = 0
```

Example:

```text
stake = 100
profit = -100
returnAmount = 0
```

---

## 3.4 VOID

The bet was voided and stake was returned.

Rules:

```text
profit = 0
returnAmount = stake
```

Void bets should not count as wins or losses.

---

## 3.5 CASHOUT

The user closed the bet before the final result.

Rules:

```text
returnAmount = cashout value informed by user
profit = returnAmount - stake
```

Cashout may be positive or negative.

Examples:

```text
stake = 100
returnAmount = 130
profit = 30
```

```text
stake = 100
returnAmount = 80
profit = -20
```

---

## 3.6 CANCELLED

The bet was cancelled by the user or by system decision.

Rules:

```text
profit = 0
returnAmount = stake
```

Cancelled bets should generally not affect performance metrics.

---

## 4. Bet Lifecycle

Initial lifecycle:

```text
PENDING -> WON
PENDING -> LOST
PENDING -> VOID
PENDING -> CASHOUT
PENDING -> CANCELLED
```

Not allowed in the first version:

```text
WON -> LOST
LOST -> WON
VOID -> WON
CASHOUT -> WON
CANCELLED -> WON
```

Future improvement:

Create a correction flow for wrong settlements.

Possible future statuses:

```text
CORRECTED
REOPENED
DELETED
ARCHIVED
```

These are out of scope for the first version.

---

## 5. Business Metrics

## 5.1 Total Stake

Total amount risked in settled bets.

Formula:

```text
totalStake = sum(stake) for settled bets
```

Pending bets should be excluded.

Void and cancelled bets may be excluded from performance calculations depending on the report.

Initial recommendation:

- Include WON, LOST, and CASHOUT in performance metrics.
- Exclude PENDING, VOID, and CANCELLED from ROI, yield, and win rate.

---

## 5.2 Total Profit

Total net result.

Formula:

```text
totalProfit = sum(profit) for settled performance bets
```

Initial performance statuses:

```text
WON
LOST
CASHOUT
```

---

## 5.3 ROI

Return on investment.

Formula:

```text
ROI = (totalProfit / totalStake) * 100
```

Rules:

- Use only settled performance bets.
- If totalStake is zero, ROI should be zero or null according to API contract.
- Initial recommendation: return zero when totalStake is zero.

---

## 5.4 Yield

Yield represents betting efficiency.

Formula:

```text
Yield = (totalProfit / totalStake) * 100
```

For the first version, ROI and Yield may use the same formula.

Future improvement:

- ROI may be based on bankroll allocation.
- Yield may remain based on betting turnover.

---

## 5.5 Win Rate

Percentage of resolved bets that were won.

Formula:

```text
winRate = (wonBets / totalWinLossBets) * 100
```

Rules:

- WON counts as win.
- LOST counts as loss.
- CASHOUT may be excluded from win rate in the first version.
- VOID and CANCELLED should be excluded.
- PENDING should be excluded.

---

## 5.6 Average Odds

Average odds of included bets.

Formula:

```text
averageOdds = sum(odds) / betsCount
```

Rules:

- Can be calculated for all bets or filtered bets.
- Dashboard should define whether it includes pending bets.
- Initial recommendation: include only settled performance bets.

---

## 5.7 Drawdown

Drawdown measures the decline from a previous bankroll peak.

Algorithm:

```text
currentBankroll = initialBankroll
peak = initialBankroll
maxDrawdown = 0

for each settled performance bet ordered by settledAt:
    currentBankroll = currentBankroll + profit

    if currentBankroll > peak:
        peak = currentBankroll

    drawdown = peak - currentBankroll

    if drawdown > maxDrawdown:
        maxDrawdown = drawdown
```

Drawdown percentage:

```text
drawdownPercentage = (drawdown / peak) * 100
```

Initial recommendation:

- Use only WON, LOST, and CASHOUT bets.
- Order by `settledAt`.
- If no initial bankroll exists, use zero-based profit curve for first MVP.

---

## 6. Dashboard Filters

Initial filters:

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

Rules:

- Filters should be optional.
- Date range should filter by `placedAt` or `settledAt` depending on endpoint purpose.
- Dashboard metrics should preferably use `settledAt` because metrics depend on resolved results.
- Bet listing may use `placedAt`.

---

## 7. MVP Domain Scope

The first MVP should include:

- User registration.
- User login.
- JWT authentication.
- Bet creation.
- Bet update while pending.
- Bet listing.
- Bet settlement.
- Bet-level profit calculation.
- Event publishing after bet creation, update, and settlement.
- Analytics projection update through events.
- Dashboard metrics with basic filters.

---

## 8. Out of Scope for First Version

Do not implement these unless explicitly requested:

- External sportsbook integration.
- Real odds import.
- Betting recommendations.
- AI prediction engine.
- Payment subscriptions.
- Multi-tenant organizations.
- Team catalog.
- League catalog.
- Sport catalog microservice.
- Multiple bankrolls.
- Bookmaker management.
- Bet slip grouping.
- Parlay/multiple bets.
- Arbitrage calculations.
- Surebet calculations.
- Kelly criterion.
- Unit staking plans.
- Social features.
- Public leaderboards.

---

## 9. Domain Implementation Guidelines

Backend agents must:

- Keep business rules in the domain layer when possible.
- Keep orchestration in the application layer.
- Keep persistence details in the infrastructure layer.
- Keep controllers thin.
- Use `BigDecimal` for financial values.
- Avoid `double` and `float` for money and odds.
- Add tests for profit calculation and bet lifecycle rules.
- Avoid normalizing sport, league, team, and market too early.
- Avoid creating catalog services in the first version.
