# Testing Strategy

## 1. Principles

- Tests prove specified behavior; they do not merely increase coverage.
- Each task starts with tests in a demonstrably failing **Red** state, except pure documentation/infrastructure tasks where a meaningful automated test does not yet exist. Record any exception in the task file.
- Implement the smallest production change that makes approved tests pass (**Green**), then refactor without behavior changes.
- Use `BigDecimal` for all money, odds, and calculated financial metrics. Tests must assert value and the domain-defined scale where scale is observable.
- Favor public behavior and business outcomes over private methods, implementation details, or framework wiring.

## 2. Test levels

| Level | Purpose | Typical scope | Tools |
| --- | --- | --- | --- |
| Domain unit | Business rules, value objects, state transitions, calculations | Pure domain; no Spring or database | JUnit 5 |
| Application unit | Use-case orchestration and port interactions | One use case with explicit collaborators | JUnit 5, Mockito only at boundaries |
| Persistence integration | Repository mapping, constraints, migrations, queries | PostgreSQL and Flyway | Testcontainers |
| API integration | HTTP contract, validation, status codes, authorization boundary | Spring application plus real persistence where needed | RestAssured, Testcontainers |
| Messaging integration | Event contract, topology, publish/consume, idempotency | RabbitMQ and relevant persistence | Testcontainers |
| Frontend | User-visible component behavior and HTTP services | Components, forms, gateway client | Angular test tools |

## 3. When each level is required

- A business rule, calculation, value object, or lifecycle transition requires domain unit tests.
- A use case that coordinates repositories, publishers, authorization inputs, or transactions requires application tests.
- A JPA mapping, Flyway migration, PostgreSQL constraint, query, or pagination/filter requires persistence integration coverage.
- A public endpoint or error response requires API integration coverage with its most important success and failure paths.
- A RabbitMQ contract or idempotent consumer change requires messaging integration coverage when practical.
- A frontend form, route guard, or API client change requires focused frontend tests when practical.

Do not replace a required integration test with a unit test merely because it is easier to write or run.

## 4. Mock policy

Mocks are permitted only for a direct external boundary of the unit under test: repositories, publishers, clocks, ID generators, remote clients, and similar ports. Do not mock domain value objects or the behavior being tested. Do not use mocks to hide mapping, transaction, persistence, serialization, security, or messaging failures that an integration test should expose.

Each mock interaction assertion must prove an observable requirement, such as “an event is published after successful persistence.” Avoid verifying incidental call order or private implementation steps.

## 5. Test design standard

Use descriptive names in the form:

```text
should_<expected_behavior>_when_<condition>
```

Structure tests using Given/When/Then, with one behavior per test where practical. Every behavior group should consider:

- a positive scenario;
- a negative/invalid scenario;
- relevant boundaries (zero, one, scale/rounding, empty data, duplicates, ownership, and invalid state transitions);
- authorization and tenant/user isolation when an endpoint is protected.

Use precise assertions. Assert the relevant returned value, status, exception type/message contract, persistence result, or emitted event. Avoid “not null,” broad predicates, and exception swallowing when a specific behavior can be asserted.

## 6. Proving Red and Green

Before production code is written, the test agent must run the narrowest relevant test command and record in the task file:

- command executed;
- failing test names;
- concise cause of failure;
- confirmation that the failure reflects missing behavior rather than a broken build.

After implementation, record the same focused command in Green. Before QA/commit, run the relevant full suite defined by the task. A failed unrelated existing test is not silently ignored: document it and obtain a human decision.

## 7. Approved-test change policy

Tests approved by the human cannot be removed, skipped, weakened, or changed by implementation to make a suite pass. A test may change only after a human explicitly approves a correction to the specification, acceptance criteria, or test itself. The task must record why, which requirement changed, and the approval.

## 8. Coverage policy

Coverage is a signal, not a quota. Prioritize every acceptance criterion, meaningful error path, business boundary, data ownership boundary, and event contract. Do not write low-value tests solely to reach a percentage target.
