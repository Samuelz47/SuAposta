# 2.2 — Bootstrap API Gateway and Auth Service

## Context

Task 2.1 created the Gradle multi-service foundation for the backend.

This task introduces the first runnable Spring Boot applications:

- API Gateway
- Auth Service

The purpose is only to establish runnable service skeletons, health behavior and package boundaries.

No business behavior, authentication flow, persistence or routing rules are implemented in this task.

The services must follow the architectural boundaries defined in `docs/architecture.md`.

## Objective

Create runnable Spring Boot skeletons for API Gateway and Auth Service.

The implementation must prove that:

- each application can start independently;
- each application exposes a health endpoint;
- Auth Service contains the documented architectural layers;
- API Gateway remains free of domain and persistence behavior;
- no authentication, user or routing feature is implemented yet.

## Acceptance criteria

### API Gateway

- [x] `api-gateway` is a runnable Spring Boot application.
- [x] The application uses Java 21.
- [x] The application starts on the port documented for API Gateway.
- [x] The application exposes a health endpoint through Spring Boot Actuator.
- [x] The health endpoint returns a successful response while the application is healthy.
- [x] The Gateway does not contain:
    - authentication logic;
    - user domain logic;
    - betting logic;
    - analytics logic;
    - persistence configuration;
    - database repositories.
- [x] No application route to backend services is configured in this task.

### Auth Service

- [x] `auth-service` is a runnable Spring Boot application.
- [x] The application uses Java 21.
- [x] The application starts on the port documented for Auth Service.
- [x] The application exposes a health endpoint through Spring Boot Actuator.
- [x] The health endpoint returns a successful response while the application is healthy.
- [x] The service contains the following top-level architectural packages:

```text
domain
application
infrastructure
presentation
```

- [x] The package structure follows `docs/architecture.md`.
- [x] No business entity or authentication behavior is implemented.
- [x] No database connection is required for the application to start.
- [x] No Flyway migration is introduced.
- [x] No user endpoint is introduced.
- [x] No JWT generation or validation is introduced.

### Independent execution

- [x] API Gateway can run without starting Auth Service.
- [x] Auth Service can run without starting API Gateway.
- [x] PostgreSQL and RabbitMQ are not required for the health tests in this task.
- [x] Tests for one service can run without starting the other service.
- [x] The complete backend build continues to pass.

## Boundary and negative cases

- [x] Gateway must not contain domain packages for users, bets or analytics.
- [x] Gateway must not access PostgreSQL.
- [x] Auth Service must not implement registration, login or token behavior.
- [x] Auth Service must not require PostgreSQL merely to load the Spring context.
- [x] No RabbitMQ integration is introduced.
- [x] No route such as `/auth/**`, `/bets/**` or `/analytics/**` is configured in Gateway.
- [x] No fake business endpoint is created only to prove that the service is running.
- [x] Health behavior must rely on infrastructure intended for health checking, such as Spring Boot Actuator.
- [x] Tests must fail if the corresponding application cannot load its Spring context.
- [x] Tests must fail if the health behavior expected by this task is unavailable.

## Out of scope

- User registration.
- Login.
- Password hashing.
- JWT creation or validation.
- Authentication filters.
- Authorization.
- Database repositories.
- Database migrations.
- API Gateway routes.
- Service discovery.
- Rate limiting.
- CORS policy beyond the minimum framework defaults required to start.
- RabbitMQ integration.
- Business endpoints.
- Betting behavior.
- Analytics behavior.
- Production deployment configuration.

## Dependencies

- Task 2.1 completed and approved.
- Gradle multi-service build available.
- Java 21 toolchain configured.
- `docs/architecture.md` approved.
- `docs/api-contracts.md` available.
- `docs/definition-of-done.md` approved.

## Expected files

The task may create or modify files related to:

```text
services/api-gateway/
services/auth-service/
build.gradle
settings.gradle
gradle.properties
docs/tasks/phase-02/02-gateway-and-auth-skeletons.md
docs/roadmap.md
```

Changes outside this scope require explicit justification.

## Expected tests

This task uses the TDD workflow defined in the project.

The blind-test agent must create the tests before production implementation.

The initial Red state must prove the absence of the required runnable applications and health behavior.

Expected test categories include:

### API Gateway

- Spring application-context test.
- Health endpoint integration test.

### Auth Service

- Spring application-context test.
- Health endpoint integration test.
- Structural test or equivalent validation proving that the required architectural packages exist.

Tests must not require:

- PostgreSQL;
- RabbitMQ;
- another backend service;
- manual application startup.

The test agent must not create production code.

## Red-state requirements

Before implementation:

- [x] Tests compile successfully.
- [x] Tests fail because the required application or behavior does not exist yet.
- [x] Tests do not fail because Gradle itself is broken.
- [x] Tests do not fail because Java 21 is unavailable in the configured toolchain.
- [x] Tests do not introduce fake production classes solely to make the test project compile.
- [x] The reason for each failing test is recorded.

Acceptable Red examples:

```text
Application class does not exist.
Spring context cannot load because the service skeleton is absent.
Expected health endpoint is unavailable.
Required package boundary is absent.
```

Unacceptable Red examples:

```text
Gradle syntax error.
Missing unrelated Docker service.
PostgreSQL connection refused.
RabbitMQ connection refused.
Test dependency not configured correctly.
```

## Implementation constraints

After human approval of the Red tests, the implementation agent must:

- implement only the minimum production code required by this task;
- preserve the approved tests;
- not weaken assertions;
- not remove or disable tests;
- not introduce behavior outside the acceptance criteria;
- not implement future authentication or routing features.

If an approved test appears inconsistent with the task, the implementation agent must stop and report the inconsistency instead of changing the test.

## Expected validation commands

The test and implementation agents should execute the relevant commands, including:

```bash
./gradlew :services:api-gateway:test
./gradlew :services:auth-service:test
./gradlew check
```

When runtime startup validation is useful, the implementation may also use the documented Gradle Boot tasks.

No test in this task should require Docker infrastructure.

## Human review checklist

Before implementation:

- [ ] Each acceptance criterion has corresponding test coverage where practical.
- [ ] The Red state is caused by missing behavior, not broken infrastructure.
- [ ] Tests do not accidentally specify future business behavior.
- [ ] Tests do not couple unnecessarily to internal implementation details.

Before final QA:

- [ ] Approved tests were not weakened.
- [ ] Gateway contains no business behavior.
- [ ] Auth Service contains the four required layers.
- [ ] Both services are independently runnable.
- [ ] Both health behaviors are validated.
- [ ] No persistence, JWT, users or routes were introduced.
- [ ] Full relevant Gradle suite passes.
- [ ] New files are visible in the Git diff.
- [ ] Task status is `READY FOR QA`.

## QA final requirements

The QA final agent must independently inspect:

- this task;
- approved tests;
- current Git diff;
- API Gateway implementation;
- Auth Service implementation;
- architecture boundaries;
- test results.

The QA agent must not:

- modify production code;
- modify approved tests;
- weaken acceptance criteria;
- implement missing behavior;
- mark the task as `DONE`;
- commit, push or merge.

The expected output is:

```text
VERDICT: APPROVED | APPROVED WITH RESERVATIONS | REJECTED

Blockers:
- ...

Important issues:
- ...

Non-blocking improvements:
- ...

Evidence:
- ...
```

## Definition of Done

Apply `docs/definition-of-done.md`.

In addition, this task is complete only when:

- [x] Blind tests were created before production implementation.
- [x] Human review approved the Red tests.
- [x] API Gateway starts successfully.
- [x] Auth Service starts successfully.
- [x] Both health behaviors pass.
- [x] Auth Service has the required architectural layers.
- [x] Gateway contains no business or persistence behavior.
- [x] Approved tests remain unchanged unless explicitly authorized by the human reviewer.
- [x] Relevant Gradle tests pass.
- [x] Human implementation review is complete.
- [x] Final QA issues `APPROVED`, or the human reviewer explicitly accepts `APPROVED WITH RESERVATIONS`.
- [x] Evidence is recorded.
- [x] Task is marked `DONE`.
- [x] Roadmap entry is marked `DONE`.
- [ ] Final commit contains only Task 2.2 changes.

## Status and evidence

| Field | Value |
| --- | --- |
| Current status | DONE |
| Specification approved by | Human request to execute the blind-test workflow / 2026-08-06 |
| Tests started at | 2026-08-06 |
| Tests completed at | 2026-08-06 |
| Human test review | Approved by explicit implementation request / 2026-08-06 |
| Implementation started at | 2026-08-06 |
| Implementation completed at | 2026-08-06 |
| Human implementation review | Approved by human validation / 2026-08-06 |
| QA verdict | APPROVED / 2026-08-06 |
| Final human decision | Approved by human / 2026-08-06 |

### Validation evidence

Updated during implementation on 2026-08-06:

- Test files created:
  - `services/api-gateway/src/test/java/com/suaposta/gateway/ApplicationTestSupport.java`
  - `services/api-gateway/src/test/java/com/suaposta/gateway/GatewayApplicationContextTest.java`
  - `services/api-gateway/src/test/java/com/suaposta/gateway/GatewayHealthIntegrationTest.java`
  - `services/api-gateway/src/test/java/com/suaposta/gateway/GatewayBoundaryTest.java`
  - `services/auth-service/src/test/java/com/suaposta/auth/ApplicationTestSupport.java`
  - `services/auth-service/src/test/java/com/suaposta/auth/AuthApplicationContextTest.java`
  - `services/auth-service/src/test/java/com/suaposta/auth/AuthHealthIntegrationTest.java`
  - `services/auth-service/src/test/java/com/suaposta/auth/AuthLayerStructureTest.java`
- Red commands:
  - `./gradlew :services:api-gateway:test :services:auth-service:test --no-daemon`
- Red failures and reasons:
  - `GatewayApplicationContextTest.should_load_application_context_when_gateway_is_started`: no Spring Boot application class exists yet.
  - `GatewayHealthIntegrationTest.should_expose_healthy_actuator_endpoint_on_documented_port`: no Spring Boot application class exists yet, so no server or health endpoint can start.
  - `GatewayHealthIntegrationTest.should_not_expose_business_routes_in_gateway_skeleton`: no Spring Boot application class exists yet, so no server can start to prove the routes are absent.
  - `GatewayBoundaryTest.should_not_contain_domain_persistence_or_business_packages`: `src/main` does not exist yet.
  - `GatewayBoundaryTest.should_not_configure_routes_databases_or_messaging_in_skeleton`: `src/main` does not exist yet.
  - `AuthApplicationContextTest.should_load_application_context_without_external_infrastructure`: no Spring Boot application class exists yet.
  - `AuthHealthIntegrationTest.should_expose_healthy_actuator_endpoint_on_documented_port`: no Spring Boot application class exists yet, so no server or health endpoint can start.
  - `AuthHealthIntegrationTest.should_not_expose_user_endpoints_in_auth_skeleton`: no Spring Boot application class exists yet, so no server can start to prove the endpoints are absent.
  - `AuthLayerStructureTest.should_define_all_documented_architectural_packages`: `src/main/java` does not exist yet.
  - `compileTestJava` succeeded for both services; failures occurred during test execution and reflect missing Task 2.2 behavior, not a broken Gradle build, missing Docker service, PostgreSQL, or RabbitMQ.
- Human test approval: Explicitly approved by the implementation request in this task / 2026-08-06.
- Production files created or changed:
  - `services/api-gateway/build.gradle` — runtime Actuator and WebFlux dependencies.
  - `services/api-gateway/src/main/java/com/suaposta/gateway/GatewayApplication.java` — Spring Boot entry point.
  - `services/api-gateway/src/main/resources/application.properties` — port `8080` and health exposure.
  - `services/auth-service/build.gradle` — runtime Actuator and Spring MVC dependencies.
  - `services/auth-service/src/main/java/com/suaposta/auth/AuthApplication.java` — Spring Boot entry point.
  - `services/auth-service/src/main/java/com/suaposta/auth/{domain,application,infrastructure,presentation}/package-info.java` — documented layer packages.
  - `services/auth-service/src/main/resources/application.properties` — port `8081` and health exposure.
- Green commands:
  - `./gradlew :services:api-gateway:test :services:auth-service:test --no-daemon` — successful.
- Full-suite result:
  - `./gradlew check --no-daemon` — successful; all four backend modules checked and Java 21 verification passed.
- API Gateway health evidence: Focused integration tests passed on port `8080`; `/actuator/health` returned HTTP `200` with `"status":"UP"`; `/auth/register`, `/auth/login`, `/bets` and `/analytics/dashboard` returned HTTP `404`.
- Auth Service health evidence: Focused integration tests passed on port `8081`; `/actuator/health` returned HTTP `200` with `"status":"UP"`; `/auth/register`, `/auth/login` and `/auth/me` returned HTTP `404`.
- Architectural package validation: `AuthLayerStructureTest` passed for `domain`, `application`, `infrastructure` and `presentation`; gateway boundary tests passed without domain, persistence, route or messaging configuration.
- Implementation completed at: 2026-08-06.
- Human implementation review: Approved by human validation / 2026-08-06.
- QA evidence:
  - VERDICT: APPROVED
  - Blockers: None.
  - Important issues: None.
  - Non-blocking improvements: None.
  - Evidence: Independently inspected the task specification, architecture rules, approved tests, production diff, Gradle dependencies, application properties and all changed paths. `./gradlew :services:api-gateway:test :services:auth-service:test --no-daemon` passed. `./gradlew check --no-daemon` passed for all four backend modules, including Java 21 verification. `git diff --check` passed. Gateway production source contains no domain, persistence, business, route, database or messaging behavior. Auth production source contains only the Spring Boot entry point and the four documented empty layer packages. Health and negative-route integration tests passed on ports 8080 and 8081 without PostgreSQL, RabbitMQ or the other service.
- Accepted reservations, if any: None.

The human approved the final QA outcome on 2026-08-06. Task 2.2 and its roadmap entry are now `DONE`. No commit, push or merge was performed.
