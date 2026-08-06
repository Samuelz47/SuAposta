# 1.2 — Provision PostgreSQL with service-owned databases

## Context

Task 1.1 established the empty Docker Compose foundation and the local environment-variable contract.

The SuAposta architecture requires one local PostgreSQL container hosting separate development databases for the initial backend services:

- `auth-service`;
- `betting-service`;
- `analytics-service`.

Each service must use its own database and database user. A service-owned database must not be owned or accessed through another service's credentials.

This task provisions PostgreSQL and its initial databases for local development. Application schemas and migrations remain the responsibility of the individual services in later phases.

## Objective

Provision a local PostgreSQL service through Docker Compose and automatically initialize one database and one database user for each initial service.

The implementation must provide:

- a healthy PostgreSQL container;
- separate service-owned databases;
- separate service-owned users;
- repeatable initialization from a clean volume;
- named-volume persistence;
- operational evidence that the databases and users were created correctly.

## Acceptance criteria

### PostgreSQL Compose service

- [x] A `postgres` service is added to the root `compose.yaml`.
- [x] The service uses an explicit PostgreSQL image version instead of an unbounded `latest` tag.
- [x] The service obtains administrative credentials from the local environment contract.
- [x] No credential is hardcoded directly in `compose.yaml`.
- [x] The image variables expected by PostgreSQL are mapped from the project environment variables.
- [x] PostgreSQL is connected to `suaposta-network`.
- [x] The internal PostgreSQL port is configurable through the environment contract.
- [x] The host-published PostgreSQL port is configurable independently from the internal port.
- [x] Only the host port required for local development is published.
- [x] No `container_name` is declared without documented justification.
- [x] A reliable PostgreSQL health check is configured.
- [x] The health check validates that PostgreSQL is ready to accept connections.
- [x] Restart and health-check settings are suitable for local development and documented when relevant.
- [x] RabbitMQ and application services are not introduced in this task.

### Persistence

- [x] PostgreSQL data is stored in a concrete named volume.
- [x] The named volume is declared in the top-level `volumes` section.
- [x] Data survives `docker compose down` followed by `docker compose up -d`.
- [x] The documentation explains that `docker compose down -v` removes the local database data.
- [x] No developer-specific absolute bind-mount path is used for PostgreSQL data.

### Database initialization

- [x] The following databases are created using the names configured in `.env`:
  - auth-service database;
  - betting-service database;
  - analytics-service database.
- [x] Each database has a dedicated database user configured through environment variables.
- [x] Each service database is owned by its corresponding service user.
- [x] Initialization runs automatically when PostgreSQL starts with a new empty volume.
- [x] Initialization does not require the developer to enter SQL manually.
- [x] Initialization scripts are versioned in the repository.
- [x] Initialization does not contain real production credentials.
- [x] Initialization fails clearly when required variables are missing or invalid.
- [x] The administrative PostgreSQL user is not used as the normal application user for all databases.

### Ownership and access isolation

- [x] The auth database is owned by the auth database user.
- [x] The betting database is owned by the betting database user.
- [x] The analytics database is owned by the analytics database user.
- [x] The initialization does not assign ownership of all databases to the administrative user.
- [x] One service user must not receive ownership of another service's database.
- [x] Cross-service access is not granted by the initialization scripts.
- [x] Default public privileges are reviewed and restricted when required to satisfy service ownership.
- [x] Validation proves each configured service user can connect to its own database.
- [x] Validation attempts to prove that a service user cannot connect to or use another service's database beyond PostgreSQL defaults and explicitly documented limitations.

### Environment contract

- [x] PostgreSQL configuration uses the variables established in `.env.example`.
- [x] `.env.example` remains safe to version.
- [x] The real `.env` remains ignored.
- [x] Database names, users, passwords and published host port are configurable.
- [x] No production hostname, database URL or credential is introduced.
- [x] The documentation distinguishes:
  - PostgreSQL host and port used by containers;
  - PostgreSQL host and port used from the developer's machine.

### Documentation

- [x] `docs/infrastructure.md` is updated with PostgreSQL startup and validation instructions.
- [x] The documentation explains how the initial databases are created.
- [x] The documentation explains that initialization scripts run only when the data directory is empty.
- [x] The documentation explains how to intentionally recreate the databases from a clean volume.
- [x] Destructive reset commands are clearly marked as destructive.
- [x] The documentation includes commands for:
  - starting PostgreSQL;
  - inspecting service health;
  - viewing PostgreSQL logs;
  - listing databases;
  - listing roles;
  - validating each service connection;
  - stopping the environment while preserving data;
  - deleting the local data volume intentionally.

## Boundary and negative cases

- [x] Local configuration does not contain production credentials or endpoints.
- [x] Missing required credentials do not silently fall back to unsafe defaults.
- [x] PostgreSQL does not use the same service user for all three databases.
- [x] A service database is not owned by another service's user.
- [x] Initialization does not depend on manual SQL execution.
- [x] Initialization does not recreate or overwrite databases every time the container restarts.
- [x] Restarting the PostgreSQL container does not erase persisted data.
- [x] Running `docker compose down` does not erase persisted data.
- [x] Running `docker compose down -v` is treated as an explicit destructive reset.
- [x] Existing initialized volumes are not silently rewritten by modified initialization scripts.
- [x] RabbitMQ is not provisioned.
- [x] Application schemas, tables and business data are not created.
- [x] No Flyway or application migration is introduced.
- [x] No database administration interface such as pgAdmin is added.

## Out of scope

- RabbitMQ provisioning.
- RabbitMQ users, exchanges, queues, bindings or policies.
- Application services.
- Application schemas and tables.
- Flyway or Liquibase migrations.
- Seed data for business entities.
- Production database provisioning.
- Production secret management.
- Backups, replicas or high availability.
- Connection pooling.
- Performance tuning.
- TLS configuration.
- pgAdmin or other database administration interfaces.
- CI/CD infrastructure.
- Kubernetes or Docker Swarm.

## Dependencies

- Task 1.1 completed and approved.
- `compose.yaml` available.
- `.env.example` available.
- `.env` ignored by Git.
- `docs/infrastructure.md` available.
- Docker daemon available for runtime validation.
- Docker Compose available through `docker compose`.

## Expected files

The task may create or update files such as:

```text
compose.yaml
.env.example
docs/infrastructure.md
docker/postgres/init/
docs/tasks/phase-01/02-postgres-databases.md
docs/roadmap.md
```

The exact initialization-script filename may be chosen by the implementation, provided its responsibility is clear.

Changes outside this scope require explicit justification.

## Expected validations

This infrastructure task does not use the application TDD Red–Green–Refactor cycle.

It requires operational validation against a real local PostgreSQL container.

The implementation must execute and record, when available:

```bash
docker compose config
docker compose --env-file .env.example config
docker compose up -d postgres
docker compose ps
docker compose logs postgres
```

The implementation must also verify PostgreSQL readiness and inspect the created databases and users.

Examples of acceptable checks include:

```bash
docker compose exec postgres \
  psql -U "$POSTGRES_SUPERUSER" -d postgres -c "\l"

docker compose exec postgres \
  psql -U "$POSTGRES_SUPERUSER" -d postgres -c "\du"
```

Each service user must be validated against its own database, for example:

```bash
docker compose exec postgres \
  psql -U "$AUTH_DB_USER" -d "$AUTH_DB_NAME" \
  -c "SELECT current_user, current_database();"
```

Equivalent checks must be performed for betting and analytics.

The implementation must also validate persistence:

1. Create a harmless validation marker or equivalent evidence.
2. Run:

```bash
docker compose down
docker compose up -d postgres
```

3. Confirm that the initialized databases and validation evidence remain available.

A destructive `docker compose down -v` must not be executed automatically without explicit human approval.

The implementation must additionally execute:

```bash
git check-ignore -v .env
git diff --check
git status
git diff
```

For new untracked files, use:

```bash
git add -N <new-files>
```

This command is only for exposing new files in the review diff. It is not final staging approval.

If the agent cannot access the Docker daemon, it must report the limitation and must not claim runtime validation passed.

## Human review checklist

Before final QA, the human reviewer must confirm:

- [x] Only PostgreSQL-related infrastructure was introduced.
- [x] RabbitMQ was not provisioned.
- [x] The PostgreSQL image version is explicitly pinned.
- [x] Credentials come from environment variables.
- [x] No real secrets were added.
- [x] A named volume is used.
- [x] Health-check behavior is reasonable.
- [x] Initialization scripts create the three configured databases.
- [x] Each database has a dedicated owner.
- [x] The administrative user is not used as the normal user for every service.
- [x] Initialization is automatic from a clean volume.
- [x] Existing data survives container recreation without volume deletion.
- [x] Destructive reset behavior is documented.
- [x] All new files are visible in the Git diff.
- [x] Runtime validation evidence is present.
- [x] The task is marked `READY FOR QA`.

## QA final requirements

The QA final agent must independently inspect:

- this task;
- the current Git diff;
- `compose.yaml`;
- `.env.example`;
- PostgreSQL initialization scripts;
- `docs/infrastructure.md`;
- runtime validation evidence;
- database ownership and connection results.

The QA agent must execute the relevant non-destructive commands when Docker access is available.

The QA agent must not:

- modify implementation files;
- weaken acceptance criteria;
- execute `docker compose down -v` without explicit human approval;
- mark the task as `DONE`;
- commit, push or merge changes.

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

- [x] All acceptance criteria are satisfied.
- [x] PostgreSQL starts and reaches a healthy state.
- [x] All three configured databases exist.
- [x] All three service database users exist.
- [x] Database ownership matches the corresponding service user.
- [x] Each service user can connect to its own database.
- [x] Cross-service ownership or unintended access is not present.
- [x] Initialization succeeds automatically from a clean volume.
- [x] Persistence after a non-destructive Compose restart is proven.
- [x] `.env` remains ignored.
- [x] No real secret is committed.
- [x] Human implementation review is complete.
- [x] Final QA issues `APPROVED`, or the human reviewer explicitly accepts an `APPROVED WITH RESERVATIONS` verdict.
- [x] Validation evidence is recorded.
- [x] The task file is marked `DONE`.
- [x] The corresponding roadmap entry is marked `DONE`.
- [ ] The final commit contains only Task 1.2 changes.

## Status and evidence

| Field | Value |
| --- | --- |
| Current status | DONE |
| Specification approved by | Human implementation request, 2026-08-06 |
| Implementation started at | 2026-08-06 |
| Implementation completed at | 2026-08-06 |
| Human implementation review | Approved by human, 2026-08-06 |
| QA verdict | APPROVED |
| Final human decision | Approved by human, 2026-08-06 |

The infrastructure task does not use the application Red–Green cycle. The user explicitly authorized direct implementation and approved the implementation diff. The permitted direct transition from `PLANNED` to `QA IN REVIEW` was applied; the roadmap is synchronized. Independent QA and final human approval remain pending.

### Validation evidence

- Files created or changed: `compose.yaml`, `.env.example`, `docker/postgres/init/01-create-service-databases.sh`, `docs/infrastructure.md`, and this task file. A local `.env` containing only placeholders was created for validation and remains ignored.
- PostgreSQL image and version: `postgres:16.4-bookworm`.
- Commands executed: `docker compose config`; `docker compose --env-file .env.example config`; `docker compose up -d postgres`; `docker compose ps`; `docker compose logs postgres`; database/role/ownership queries; own-service connection checks; six cross-service denial checks; persistence marker plus `docker compose down` and `docker compose up -d postgres`; `git check-ignore -v .env`; `git diff --check`; `git status`.
- `docker compose config` result: Passed with one `postgres` service, the shared network, and the named volume. The `.env.example` variant also passed.
- PostgreSQL health result: `docker compose ps` reported `Up ... (healthy)` before and after the non-destructive restart.
- Databases created: `suaposta_auth`, `suaposta_betting`, and `suaposta_analytics`.
- Roles created: `suaposta_auth`, `suaposta_betting`, and `suaposta_analytics`; all are non-superuser service logins. `suaposta_admin` remains the administrative superuser.
- Database ownership: each database is owned by its corresponding service role; no service role owns another service database.
- Service-user connection checks: all three users connected over TCP to their own configured database and returned matching `current_user`/`current_database` values.
- Cross-service access checks: all six service-user-to-other-database attempts were denied with `permission denied for database` / missing `CONNECT` privilege.
- Persistence validation: the `suaposta-task-1.2-persistence-marker` database comment and all three databases/owners remained after `docker compose down` followed by `docker compose up -d postgres`; initialization did not rerun on the existing volume.
- `.env` ignore verification: passed; `.gitignore:19:.env` matches `.env`.
- Docker daemon limitation, if any: the initial sandboxed daemon check was denied; escalated Docker access was approved and all runtime validations passed. `docker compose down -v` was not executed.
- Human review notes: Implementation diff approved by human on 2026-08-06; task advanced to `QA IN REVIEW`.
- QA evidence: Independent QA audit passed on 2026-08-06. Non-destructive Compose, PostgreSQL health, ownership, role privilege, own-service connection, cross-service denial, syntax, diff, and ignore checks passed. Human approved the QA outcome on 2026-08-06.
- Accepted reservations, if any: None.
