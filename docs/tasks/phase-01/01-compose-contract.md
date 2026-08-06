# 1.1 — Define Docker Compose configuration and environment contract

## Context

The SuAposta repository does not yet contain application or infrastructure services.

Before provisioning PostgreSQL and RabbitMQ, the project needs a consistent local infrastructure contract defining:

- the root Docker Compose structure;
- shared network conventions;
- future volume conventions;
- environment-variable naming;
- internal and host port conventions;
- secret-handling rules;
- standard local Docker commands.

Local infrastructure must follow `docs/architecture.md` and must not expose hardcoded production secrets.

This task establishes only the empty Compose foundation and environment contract. PostgreSQL and RabbitMQ are provisioned in later tasks.

## Objective

Create the initial Docker Compose foundation and environment-variable contract required for the incremental provisioning of local infrastructure.

The result must prepare the repository for:

- PostgreSQL in Task 1.2;
- RabbitMQ in Task 1.3;
- future application services.

No infrastructure container must be provisioned in this task.

## Acceptance criteria

### Docker Compose foundation

- [ ] A valid `compose.yaml` exists at the repository root.
- [ ] The Compose file uses the current Compose specification and does not declare the legacy `version` property.
- [ ] The Compose project name is defined consistently as `suaposta`.
- [ ] The Compose file contains an empty `services` section because no service is provisioned in this task.
- [ ] A shared bridge network named `suaposta-network` is declared for future container communication.
- [ ] A top-level `volumes` section is reserved for future persistent services.
- [ ] No concrete named volume is required in this task.
- [ ] PostgreSQL is not provisioned in this task and belongs exclusively to Task 1.2.
- [ ] RabbitMQ is not provisioned in this task and belongs exclusively to Task 1.3.
- [ ] No application service is added.
- [ ] No `container_name` is defined without an explicit documented justification.
- [ ] The Compose file contains no absolute path tied to a developer's machine.
- [ ] `docker compose config` processes the current foundation successfully.

### Environment contract

- [ ] A versioned `.env.example` file defines the local environment contract.
- [ ] The real `.env` file remains ignored by Git.
- [ ] `.env.example` contains only safe local-development values or placeholders.
- [ ] No production credential, token, endpoint, or secret is included.
- [ ] Environment-variable names use `UPPER_SNAKE_CASE`.
- [ ] Variables are grouped and documented by responsibility.
- [ ] PostgreSQL variables are reserved for Task 1.2 without provisioning PostgreSQL.
- [ ] RabbitMQ variables are reserved for Task 1.3 without provisioning RabbitMQ.
- [ ] Internal container ports and host-published ports are represented by distinct variables when applicable.
- [ ] Future containers must use Compose service names, such as `postgres` and `rabbitmq`, for internal communication.
- [ ] The environment contract does not use `localhost`, `127.0.0.1`, or fixed container IP addresses for container-to-container communication.

### Documentation

- [ ] Local infrastructure instructions are documented in `docs/infrastructure.md` or an equivalent approved location.
- [ ] The documentation explains how to create the local `.env` file from `.env.example`.
- [ ] The documentation lists Docker and Docker Compose as prerequisites.
- [ ] Java, Node.js, Gradle, Angular, and application-specific tools are explicitly not required for this task.
- [ ] The documentation explains the purpose of:
  - `docker compose config`;
  - `docker compose up -d`;
  - `docker compose ps`;
  - `docker compose logs`;
  - `docker compose down`;
  - `docker compose down -v`.
- [ ] The documentation states that `docker compose down -v` is destructive for local persisted data.
- [ ] The documentation explains that `depends_on` alone does not prove that a dependency is ready.
- [ ] The documentation defines health checks as a requirement for later infrastructure services when a reliable readiness mechanism exists.
- [ ] The documentation explicitly states that PostgreSQL belongs to Task 1.2 and RabbitMQ belongs to Task 1.3.

## Boundary and negative cases

- [ ] The default local configuration must not contain production credentials.
- [ ] No real `.env` file is committed.
- [ ] No PostgreSQL or RabbitMQ container is introduced prematurely.
- [ ] No concrete database, database user, RabbitMQ exchange, queue, binding, or topology is created.
- [ ] No service publishes ports before a real service requiring host access is introduced.
- [ ] No persistent data is created because no persistent service exists yet.
- [ ] No file from the previous implementation is copied into the new baseline.
- [ ] No infrastructure file references a developer-specific absolute path.
- [ ] Empty `services` and `volumes` declarations must not be interpreted as provisioned infrastructure.

## Out of scope

- Provisioning PostgreSQL.
- Creating PostgreSQL databases, schemas, roles, users, or permissions.
- Provisioning RabbitMQ.
- Creating RabbitMQ exchanges, queues, bindings, users, virtual hosts, or policies.
- Creating named volumes for services that do not yet exist.
- Creating application services.
- Installing Java, Gradle, Node.js, Angular, PostgreSQL clients, or RabbitMQ clients.
- Creating Dockerfiles for application services.
- Creating automated application tests.
- Creating CI/CD pipelines.
- Configuring production infrastructure.
- Configuring Kubernetes or Docker Swarm.
- Implementing production-grade secret management.
- Running destructive Docker commands without explicit human approval.

## Dependencies

- Phase 0 approved.
- `AGENTS.md` available.
- `docs/architecture.md` approved.
- `docs/roadmap.md` approved.
- `docs/definition-of-done.md` approved.
- `.gitignore` available and configured to ignore `.env`.
- Docker and Docker Compose available for operational validation.

## Expected files

The task may create or update only the files required for the local infrastructure contract, including:

```text
compose.yaml
.env.example
.gitignore
docs/infrastructure.md
docs/tasks/phase-01/01-compose-contract.md
docs/roadmap.md
```

## Expected validations

This task does not use the application TDD cycle because no application behavior is implemented.

The implementation must execute and record, when available:

```bash
docker compose config
docker compose --env-file .env.example config
git check-ignore -v .env
git diff --check
git status
git diff
```

For new untracked files, the implementation agent must make them visible in the review diff using:

```bash
git add -N <new-files>
```

This command is used only to expose file contents in `git diff`. It must not be treated as final staging approval.

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