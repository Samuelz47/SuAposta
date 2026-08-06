# Local infrastructure contract

This document defines the initial local Docker Compose contract for SuAposta. It is intentionally limited to the shared structure and environment configuration required before provisioning infrastructure services.

## Prerequisites

- Docker installed.
- Docker Compose available through `docker compose`.
- Docker daemon running.

Java, Node.js, and application-specific tools are not required for this task.

## Preparation

From the repository root, copy the safe template to the ignored local environment file:

```bash
cp .env.example .env
```

Adjust local placeholder values in `.env` when needed. The `.env.example` file is safe to version and contains no production credentials. The real `.env` file must remain local and ignored by Git.

## Compose contract

The root `compose.yaml` is the single Compose contract for local development. At this stage it has no application or infrastructure services. PostgreSQL belongs to Task 1.2 and RabbitMQ belongs to Task 1.3; they must be added to this same file rather than provisioned through independent Compose files.

The file already defines the shared `suaposta-network` bridge network and the named-volume section for future persistence. No ports are published until a service needs host access. Future container-to-container communication must use service names such as `postgres` or `rabbitmq`, never `localhost`: inside a container, `localhost` points back to that same container.

The environment template reserves configurable hosts, ports, database names, users, and passwords for the next infrastructure tasks. The current Compose file does not consume those variables because no service is provisioned yet.

Health checks should be added when PostgreSQL and RabbitMQ are introduced and provide a reliable readiness mechanism. `depends_on` alone must not be treated as proof that a dependency is ready.

## Standard commands

Validate the Compose structure without starting containers:

```bash
docker compose config
```

Validate that the current empty Compose foundation can be processed
without provisioning containers:

```bash
docker compose up -d
```

List container status:

```bash
docker compose ps
```

Show service logs:

```bash
docker compose logs
```

Stop and remove containers and the Compose network, preserving named volumes:

```bash
docker compose down
```

Stop and remove containers, the Compose network, and named volumes:

```bash
docker compose down -v
```

`docker compose down -v` is destructive for local persisted data. Use it only when that data can be discarded or recreated.

## Decisions and limitations

- The Compose file has no `version` property and uses the current Compose specification.
- Service and resource names are independent of the developer's computer.
- `container_name` is intentionally not used; Compose manages container names within the project.
- No secrets, production endpoints, application services, PostgreSQL, RabbitMQ, or credentials files are mounted in this task.
- The empty service and volume declarations establish the contract but do not start containers until a later task adds a real service.
