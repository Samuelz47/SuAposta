# Local infrastructure contract

This document defines the local Docker Compose contract for SuAposta. Task 1.2 provisions one PostgreSQL container with three service-owned databases, and Task 1.3 adds one RabbitMQ broker for local infrastructure validation. Application services and application messaging topology remain outside these tasks.

## Prerequisites

- Docker installed.
- Docker Compose available through `docker compose`.
- Docker daemon running.

Java, Node.js, application services, Flyway, and a host-installed PostgreSQL client are not required. The operational `psql` commands below run inside the PostgreSQL container.

## Preparation

From the repository root, create the ignored local environment file:

```bash
cp .env.example .env
```

Replace the local placeholder passwords in `.env` when needed. `.env.example` is safe to version and contains only local-development placeholders. The real `.env` must remain local and ignored by Git.

The PostgreSQL variables are grouped in `.env.example` by responsibility:

- `POSTGRES_HOST=postgres` and `POSTGRES_PORT` describe how containers reach PostgreSQL through the Compose network.
- `POSTGRES_HOST_PORT` is the independently configurable port published on the developer's machine.
- `POSTGRES_SUPERUSER`, `POSTGRES_SUPERUSER_PASSWORD`, and `POSTGRES_SUPERUSER_DB` configure the local administrative connection.
- `AUTH_DB_*`, `BETTING_DB_*`, and `ANALYTICS_DB_*` configure each service database and its dedicated owner.

The RabbitMQ variables configure the local broker:

- `RABBITMQ_HOST=rabbitmq` is the Compose-network hostname used by future application containers.
- `RABBITMQ_AMQP_PORT` is the AMQP listener port inside the Compose network.
- `RABBITMQ_HOST_AMQP_PORT` is the independently configurable AMQP port published on the developer's machine.
- `RABBITMQ_MANAGEMENT_HOST_PORT` is the management UI port published on the developer's machine; the broker listens on `15672` inside the container.
- `RABBITMQ_DEFAULT_USER` and `RABBITMQ_DEFAULT_PASS` configure local-development access only.

Inside a container, use `postgres`/`POSTGRES_PORT` for PostgreSQL and `rabbitmq`/`RABBITMQ_AMQP_PORT` for RabbitMQ. From the developer's machine, use `127.0.0.1` with the corresponding published host port. Do not use `localhost` for container-to-container communication.

## Local application runtime contract

The current Compose file provisions only PostgreSQL and RabbitMQ. It does not
start application services, but the local application processes have a defined
runtime contract. The Phase 8 frontend communicates directly from
`http://localhost:4200` to the API Gateway at `http://localhost:8080` through
Gateway CORS; there is no global API prefix and no Angular development proxy.

The application values in `.env.example` are safe local placeholders. When
launching a Spring process, provide only the variables for that process. In
particular, do not export the Analytics `SPRING_DATASOURCE_*` values into the
Auth or Betting process: those services also recognize the standard Spring
datasource names and would otherwise use the Analytics datasource.

### Shared JWT and Gateway configuration

`JWT_SECRET` is required by both `auth-service` and `api-gateway`; it must have
the same local value in both processes. It is the source for Auth token signing
and the Gateway `gateway.jwt.secret` validation property. The value in
`.env.example` is only a placeholder and must never be used in production.

The Gateway has no global API prefix and listens on `http://localhost:8080` by
default. Its downstream URLs are configurable with these actual property-backed
environment variables (the shown values are the current defaults):

| Gateway target | Environment variable | Default |
| --- | --- | --- |
| Auth service | `AUTH_SERVICE_URL` | `http://localhost:8081` |
| Betting service | `BETTING_SERVICE_URL` | `http://localhost:8082` |
| Analytics service | `ANALYTICS_SERVICE_URL` | `http://localhost:8083` |

### Host-process datasource configuration

For services running from the developer machine, use `127.0.0.1` and the
published PostgreSQL port, not the Compose-network hostname `postgres`.

| Service | Required local datasource variables | Example target |
| --- | --- | --- |
| Auth | `AUTH_DB_JDBC_URL`, `AUTH_DB_USER`, `AUTH_DB_PASSWORD` | `jdbc:postgresql://127.0.0.1:5432/suaposta_auth` |
| Betting | `BETTING_DB_JDBC_URL`, `BETTING_DB_USER`, `BETTING_DB_PASSWORD` | `jdbc:postgresql://127.0.0.1:5432/suaposta_betting` |
| Analytics | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | `jdbc:postgresql://127.0.0.1:5432/suaposta_analytics` |

The Auth and Betting services also support the standard Spring datasource
properties, but the service-specific variable sets above avoid cross-service
datasource collisions in a shared local environment. Analytics requires the
standard Spring values shown in the table: its persistence configuration and
controllers activate only when `spring.datasource.url`,
`spring.datasource.username`, and `spring.datasource.password` are configured.

### RabbitMQ configuration for application processes

Betting publishes events and Analytics consumes them. For host-run application
processes, Spring Boot maps the following environment names to the current
`spring.rabbitmq.*` bindings:

| Spring property | Environment variable | Local value |
| --- | --- | --- |
| `spring.rabbitmq.host` | `SPRING_RABBITMQ_HOST` | `127.0.0.1` |
| `spring.rabbitmq.port` | `SPRING_RABBITMQ_PORT` | `5672` |
| `spring.rabbitmq.username` | `SPRING_RABBITMQ_USERNAME` | `suaposta` |
| `spring.rabbitmq.password` | `SPRING_RABBITMQ_PASSWORD` | local placeholder |

Analytics activates its RabbitMQ listener only when both its datasource and
`spring.rabbitmq.host` are configured. If a published host port differs from
`5432` or `5672`, update the corresponding JDBC URL or Spring RabbitMQ port in
the ignored local `.env` before launching the affected process.

## PostgreSQL Compose service

The root `compose.yaml` uses the explicit `postgres:16.4-bookworm` image. PostgreSQL is attached to the shared `suaposta-network`, publishes only the local-development PostgreSQL port, and stores its data in the named `postgres-data` volume. No `container_name` is used.

The service command configures PostgreSQL to listen on `POSTGRES_PORT` inside the container. The host mapping is `${POSTGRES_HOST_PORT}:${POSTGRES_PORT}`, so the two ports can be changed independently. The health check uses `pg_isready` against the configured internal port and reports readiness only after PostgreSQL accepts connections.

## RabbitMQ Compose service

The root `compose.yaml` adds the explicit `rabbitmq:3.13.7-management` image. RabbitMQ is attached to `suaposta-network`, publishes the configured AMQP port and management UI port, and stores its local state in the named `rabbitmq-data` volume. No `container_name` is used.

The read-only `docker/rabbitmq/rabbitmq.conf` mount configures only the AMQP listener port from `RABBITMQ_AMQP_PORT`. It does not declare exchanges, queues, bindings, consumers, publishers, or application events; those belong to later messaging tasks.

The `rabbitmq-diagnostics -q ping` health check reports readiness only after the broker is running. The management image exposes the management UI at `http://127.0.0.1:${RABBITMQ_MANAGEMENT_HOST_PORT}` from the developer's machine. The default local credentials come from `.env` and must not be reused in production.

## Database initialization and ownership

`docker/postgres/init/01-create-service-databases.sh` is mounted into `/docker-entrypoint-initdb.d` read-only. The official PostgreSQL image executes it automatically only when the data directory is initialized for the first time, meaning when `postgres-data` is empty.

The script validates required values and PostgreSQL identifiers before it creates:

- the `AUTH_DB_NAME` database owned by `AUTH_DB_USER`;
- the `BETTING_DB_NAME` database owned by `BETTING_DB_USER`;
- the `ANALYTICS_DB_NAME` database owned by `ANALYTICS_DB_USER`.

Each service role is a non-superuser login role. Database `PUBLIC` privileges are revoked, cross-service database privileges are explicitly revoked, and only the corresponding service owner receives `CONNECT`. The default `public` schema is kept available to the database owner for later application migrations, but its privileges are not granted to `PUBLIC`.

Task 1.2 creates databases and roles only. It does not create application tables, application schemas, business data, Flyway migrations, RabbitMQ resources, or Java services. RabbitMQ is provisioned separately by Task 1.3 without application topology. Changing the initialization script does not rewrite an already initialized volume; recreate the volume intentionally when testing a clean initialization.

## Compose validation without starting containers

With `.env` present, validate the normal Compose contract:

```bash
docker compose config
```

Validate the safe versioned template directly:

```bash
docker compose --env-file .env.example config
```

The output must contain exactly the `postgres` and `rabbitmq` services, the `suaposta-network` network, and the `postgres-data` and `rabbitmq-data` volumes. It must not contain application services or RabbitMQ application topology.

## Start the Phase 1 infrastructure and inspect health

Load local variables into the current shell for the validation commands:

```bash
set -a
. ./.env
set +a
```

Start PostgreSQL and RabbitMQ together:

```bash
docker compose up -d
```

Inspect both service health and the container logs:

```bash
docker compose ps
docker compose logs postgres
docker compose logs rabbitmq
```

`docker compose ps` should report both services as `healthy`. The PostgreSQL logs should show the initialization script completing on a clean volume and PostgreSQL ready to accept connections. RabbitMQ logs should show the node starting without a fatal error. A successful `docker compose up -d` alone is not a smoke check; an unhealthy or exited service must fail the validation.

Run an explicit health and reachability smoke check:

```bash
assert_healthy() {
  local service="$1"
  local container_id
  container_id="$(docker compose ps -q "$service")"

  test -n "$container_id" || {
    echo "ERROR: $service has no container"
    return 1
  }

  test "$(docker inspect --format '{{.State.Health.Status}}' "$container_id")" = healthy || {
    echo "ERROR: $service is not healthy"
    docker compose ps "$service"
    return 1
  }
}

assert_healthy postgres
assert_healthy rabbitmq

docker compose exec -T postgres \
  pg_isready -U "$POSTGRES_SUPERUSER" -d "$POSTGRES_SUPERUSER_DB" -h 127.0.0.1 -p "$POSTGRES_PORT"

docker compose exec -T rabbitmq \
  rabbitmq-diagnostics -q ping

docker compose exec -T rabbitmq \
  rabbitmq-diagnostics listeners
```

The first command proves PostgreSQL accepts connections; the second proves RabbitMQ's node is reachable; and the final command displays the configured AMQP and management listeners. A failure in any command must make the smoke check fail.

List the databases and roles using the administrative user:

```bash
docker compose exec -T postgres \
  psql --no-psqlrc -U "$POSTGRES_SUPERUSER" -d "$POSTGRES_SUPERUSER_DB" -c '\l'

docker compose exec -T postgres \
  psql --no-psqlrc -U "$POSTGRES_SUPERUSER" -d "$POSTGRES_SUPERUSER_DB" -c '\du'
```

Inspect database ownership explicitly:

```bash
docker compose exec -T postgres \
  psql --no-psqlrc -U "$POSTGRES_SUPERUSER" -d "$POSTGRES_SUPERUSER_DB" \
  -c "SELECT datname, pg_get_userbyid(datdba) AS owner FROM pg_database WHERE datname IN ('$AUTH_DB_NAME', '$BETTING_DB_NAME', '$ANALYTICS_DB_NAME') ORDER BY datname;"
```

## Validate service-user connections

The following checks use TCP inside the container and pass each configured password only to that one `psql` process. Each command must print its matching `current_user` and `current_database`:

```bash
docker compose exec -T -e PGPASSWORD="$AUTH_DB_PASSWORD" postgres \
  psql --no-psqlrc -h 127.0.0.1 -p "$POSTGRES_PORT" \
  -U "$AUTH_DB_USER" -d "$AUTH_DB_NAME" \
  -c 'SELECT current_user, current_database();'

docker compose exec -T -e PGPASSWORD="$BETTING_DB_PASSWORD" postgres \
  psql --no-psqlrc -h 127.0.0.1 -p "$POSTGRES_PORT" \
  -U "$BETTING_DB_USER" -d "$BETTING_DB_NAME" \
  -c 'SELECT current_user, current_database();'

docker compose exec -T -e PGPASSWORD="$ANALYTICS_DB_PASSWORD" postgres \
  psql --no-psqlrc -h 127.0.0.1 -p "$POSTGRES_PORT" \
  -U "$ANALYTICS_DB_USER" -d "$ANALYTICS_DB_NAME" \
  -c 'SELECT current_user, current_database();'
```

Validate cross-service isolation by requiring the following attempts to fail with a database permission error:

```bash
assert_cross_service_denied() {
  local source_user="$1"
  local source_password="$2"
  local target_database="$3"

  if docker compose exec -T -e PGPASSWORD="$source_password" postgres \
      psql --no-psqlrc -h 127.0.0.1 -p "$POSTGRES_PORT" \
      -U "$source_user" -d "$target_database" -c 'SELECT 1;'; then
    echo "ERROR: $source_user unexpectedly connected to $target_database"
    return 1
  fi

  echo "Expected denial: $source_user cannot connect to $target_database"
}

assert_cross_service_denied "$AUTH_DB_USER" "$AUTH_DB_PASSWORD" "$BETTING_DB_NAME"
assert_cross_service_denied "$AUTH_DB_USER" "$AUTH_DB_PASSWORD" "$ANALYTICS_DB_NAME"
assert_cross_service_denied "$BETTING_DB_USER" "$BETTING_DB_PASSWORD" "$AUTH_DB_NAME"
assert_cross_service_denied "$BETTING_DB_USER" "$BETTING_DB_PASSWORD" "$ANALYTICS_DB_NAME"
assert_cross_service_denied "$ANALYTICS_DB_USER" "$ANALYTICS_DB_PASSWORD" "$AUTH_DB_NAME"
assert_cross_service_denied "$ANALYTICS_DB_USER" "$ANALYTICS_DB_PASSWORD" "$BETTING_DB_NAME"
```

This validation is intentionally about database-level access. The administrative PostgreSQL superuser can still access every local database by design; it is not a normal application user.

## Validate persistence

A database comment is used as a harmless validation marker; no application table or schema is created:

```bash
docker compose exec -T postgres \
  psql --no-psqlrc -U "$POSTGRES_SUPERUSER" -d "$POSTGRES_SUPERUSER_DB" \
  -c "COMMENT ON DATABASE \"$AUTH_DB_NAME\" IS 'suaposta-task-1.2-persistence-marker';"

docker compose down
docker compose up -d

docker compose exec -T postgres \
  psql --no-psqlrc -U "$POSTGRES_SUPERUSER" -d "$POSTGRES_SUPERUSER_DB" \
  -c "SELECT datname, shobj_description(oid, 'pg_database') AS marker FROM pg_database WHERE datname = '$AUTH_DB_NAME';"
```

The three databases, their owners, and the marker must still be present after `docker compose down` followed by `docker compose up -d`. `docker compose down` removes containers and the network but preserves both `postgres-data` and `rabbitmq-data`.

## Stop or intentionally reset local data

Stop the environment while preserving the named volume:

```bash
docker compose down
```

**Destructive reset — permanently deletes the local PostgreSQL and RabbitMQ data volumes:**

```bash
docker compose down -v
docker compose up -d
```

Use `docker compose down -v` only when local data can be discarded or recreated. The PostgreSQL initialization script runs again after this reset because the data directory is empty. RabbitMQ also starts with a clean local data directory.

## Decisions and limitations

- The Compose file uses the current specification and no legacy `version` property.
- Service and resource names are independent of the developer's computer.
- `container_name` is intentionally not used; Compose manages container names within the project.
- `.env.example` contains only local-development hostnames, URLs, and credential/secret placeholders; it contains no production value.
- Task 1.2 provisions PostgreSQL and Task 1.3 provisions RabbitMQ; application services are added in later phases.
- RabbitMQ application topology is intentionally not provisioned by Task 1.3.
- No destructive Docker command is run automatically by the implementation or validation workflow.
