### Decimal precision

Financial calculations must use `BigDecimal`.

- Monetary values use scale `2`.
- Odds use scale `4`.
- Monetary calculation results use scale `2`.
- Rounding mode is `HALF_UP`.
- `double` and `float` must never be used for stake, odds, profit, return amount, or intermediate financial calculations.

Input values may contain fewer decimal places and are normalized to the documented scale.

Values requiring more precision than the documented scale are rounded using `HALF_UP`.

Examples:

```text
Stake: 100      -> 100.00
Stake: 25.5     -> 25.50

Odds: 2.1       -> 2.1000
Odds: 1.85555   -> 1.8556

The invariant must be enforced when the value object is created.

Invalid examples:

```text
0
0.00
-0.01
-100
```

### Odds

Odds represent the decimal multiplier used by a won bet.

Rules:

* must use `BigDecimal`;
* must be strictly greater than `1`;
* scale is `4`;
* values are normalized using `RoundingMode.HALF_UP`;
* `double` and `float` must not be used.

Examples:

```text
2.1     -> 2.1000
1.85    -> 1.8500
2.12555 -> 2.1256
```

The invariant must be enforced when the value object is created.

Invalid examples:

```text
1
1.0000
0.99
0
-1
```

## Financial precision contract

All financial domain calculations must use `BigDecimal`.

### Money

Applies to:

* stake;
* profit;
* return amount;
* cashout return amount.

Rules:

```text
scale = 2
rounding = HALF_UP
```

### Odds

Rules:

```text
scale = 4
rounding = HALF_UP
```

### Settlement calculations

Intermediate calculations must remain `BigDecimal`.

Final monetary results must be normalized to:

```text
scale = 2
rounding = HALF_UP
```

`double` and `float` must not participate in financial calculations.

## Bet initial state

A newly created valid Bet must start as:

```text
status = PENDING
profit = null
returnAmount = null
settledAt = null
```

A pending Bet has not yet produced a financial result.

## Settlement statuses

The final statuses supported by the first version are:

```text
WON
LOST
VOID
CASHOUT
CANCELLED
```

Only a Bet whose current status is:

```text
PENDING
```

may transition to one of these final statuses.

## Settlement calculation contract

### WON

A won Bet calculates:

```text
returnAmount = stake × odds
profit = returnAmount − stake
```

Equivalent profit formula:

```text
profit = stake × (odds − 1)
```

Final `returnAmount` and `profit` use monetary scale `2` and `HALF_UP`.

Example:

```text
stake = 100.00
odds = 2.1000

returnAmount = 210.00
profit = 110.00
```

### LOST

A lost Bet calculates:

```text
returnAmount = 0.00
profit = -stake
```

Example:

```text
stake = 100.00

returnAmount = 0.00
profit = -100.00
```

### VOID

A void Bet returns the original stake:

```text
returnAmount = stake
profit = 0.00
```

Example:

```text
stake = 100.00

returnAmount = 100.00
profit = 0.00
```

### CANCELLED

A cancelled Bet returns the original stake:

```text
returnAmount = stake
profit = 0.00
```

Example:

```text
stake = 100.00

returnAmount = 100.00
profit = 0.00
```

### CASHOUT

For CASHOUT, the user informs the return amount.

The domain derives profit:

```text
returnAmount = cashoutReturn
profit = cashoutReturn − stake
```

Example with positive result:

```text
stake = 100.00
cashoutReturn = 130.00

returnAmount = 130.00
profit = 30.00
```

Example with negative result:

```text
stake = 100.00
cashoutReturn = 80.00

returnAmount = 80.00
profit = -20.00
```

CASHOUT may therefore produce:

* positive profit;
* zero profit;
* negative profit.

A CASHOUT settlement requires an explicit return amount.

A missing CASHOUT return amount must be rejected by the domain.

The user does not directly inform `profit`; the domain always derives it from:

```text
cashoutReturn − stake
```

Do not introduce additional CASHOUT restrictions unless explicitly documented.

## Lifecycle rules

Allowed transitions:

```text
PENDING -> WON
PENDING -> LOST
PENDING -> VOID
PENDING -> CASHOUT
PENDING -> CANCELLED
```

A Bet already in any final state is settled.

A settled Bet cannot be settled again.

Examples of forbidden behavior:

```text
WON -> LOST
LOST -> WON
VOID -> WON
CASHOUT -> WON
CANCELLED -> WON
```

A repeated settlement attempt must:

* be rejected by the domain;
* preserve the existing final status;
* preserve the existing profit;
* preserve the existing return amount;
* not partially mutate the Bet before failure.

Correction and reopening flows are outside this task.

## Acceptance criteria

* [ ] Stake uses `BigDecimal`.
* [ ] Stake must be strictly greater than zero.
* [ ] Stake is normalized to monetary scale `2` with `HALF_UP`.
* [ ] Odds uses `BigDecimal`.
* [ ] Odds must be strictly greater than one.
* [ ] Odds is normalized to scale `4` with `HALF_UP`.
* [ ] `double` and `float` are not used for financial domain calculations.
* [ ] A new Bet starts as `PENDING`.
* [ ] A new pending Bet has `profit = null`.
* [ ] A new pending Bet has `returnAmount = null`.
* [ ] A new pending Bet has `settledAt = null`.
* [ ] WON profit is `stake × odds − stake`.
* [ ] WON return amount is `stake × odds`.
* [ ] LOST profit is `−stake`.
* [ ] LOST return amount is zero.
* [ ] VOID profit is zero.
* [ ] VOID return amount equals stake.
* [ ] CANCELLED profit is zero.
* [ ] CANCELLED return amount equals stake.
* [ ] CASHOUT uses an explicitly informed return amount.
* [ ] CASHOUT profit is calculated as `cashoutReturn − stake`.
* [ ] CASHOUT without return amount is rejected.
* [ ] Final monetary values use scale `2` and `HALF_UP`.
* [ ] Only `PENDING` bets may settle.
* [ ] Each documented final status is reachable from `PENDING`.
* [ ] A settled Bet cannot settle again.
* [ ] Failed repeated settlement does not modify the previous settlement result.

## Boundary and negative cases

Tests must cover at least:

### Stake

* positive integer value;
* positive decimal value;
* value requiring rounding;
* zero;
* negative value.

### Odds

* value greater than one;
* decimal value;
* value requiring rounding;
* exactly one;
* below one;
* zero;
* negative value.

### WON

* normal calculation;
* decimal stake;
* decimal odds;
* calculation requiring monetary rounding.

### LOST

* negative profit;
* zero return amount.

### VOID

* zero profit;
* stake returned.

### CANCELLED

* zero profit;
* stake returned.

### CASHOUT

* positive profit;
* negative profit;
* zero profit;
* return amount requiring monetary rounding;
* missing return amount.

### Lifecycle

* `PENDING -> WON`;
* `PENDING -> LOST`;
* `PENDING -> VOID`;
* `PENDING -> CASHOUT`;
* `PENDING -> CANCELLED`;
* repeated settlement from every final status.

## Domain purity

All behavior in this task must remain inside the domain layer.

Tests must not require:

* Spring Context;
* controllers;
* Bean Validation;
* HTTP;
* repositories;
* JPA;
* databases;
* Testcontainers;
* RabbitMQ;
* external services;
* mocks.

Domain tests must be deterministic and execute without infrastructure.

## Out of scope

* Repositories.
* JPA entities.
* Database migrations.
* HTTP APIs.
* DTOs.
* Authentication.
* Ownership authorization.
* Bet creation API.
* Bet listing.
* Bet retrieval.
* Bet update API.
* RabbitMQ.
* Domain event publishing.
* Analytics aggregation.
* Settlement correction.
* Reopening settled bets.

## Dependencies

* Phase 2 Betting Service skeleton.
* `docs/domain.md`.

Task 5.2 depends on the domain behavior established here.

## Expected tests

Pure domain unit tests, without mocks, covering:

* Stake invariants;
* Odds invariants;
* decimal normalization;
* rounding;
* initial Bet state;
* WON settlement;
* LOST settlement;
* VOID settlement;
* CANCELLED settlement;
* CASHOUT settlement;
* missing CASHOUT return;
* every valid lifecycle transition;
* repeated settlement protection;
* preservation of state after rejected settlement.

Tests should initially be Red because the Task 5.1 domain implementation does not yet exist.

## Definition of Done

Apply `docs/definition-of-done.md`.

## Status and evidence

Use the status table from `docs/tasks/TEMPLATE.md`.
