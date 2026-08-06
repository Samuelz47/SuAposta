# Local infrastructure contract

This document defines the local Docker Compose contract for SuAposta. Task 1.2 provisions one PostgreSQL container with three service-owned databases. RabbitMQ and application services remain outside this task.

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

Inside a container, use `postgres` as the host and `POSTGRES_PORT` as the port. From the developer's machine, use `127.0.0.1` and `POSTGRES_HOST_PORT`. Do not use `localhost` for container-to-container communication.

## PostgreSQL Compose service

The root `compose.yaml` uses the explicit `postgres:16.4-bookworm` image. PostgreSQL is attached to the shared `suaposta-network`, publishes only the local-development PostgreSQL port, and stores its data in the named `postgres-data` volume. No `container_name` is used.

The service command configures PostgreSQL to listen on `POSTGRES_PORT` inside the container. The host mapping is `${POSTGRES_HOST_PORT}:${POSTGRES_PORT}`, so the two ports can be changed independently. The health check uses `pg_isready` against the configured internal port and reports readiness only after PostgreSQL accepts connections.

## Database initialization and ownership

`docker/postgres/init/01-create-service-databases.sh` is mounted into `/docker-entrypoint-initdb.d` read-only. The official PostgreSQL image executes it automatically only when the data directory is initialized for the first time, meaning when `postgres-data` is empty.

The script validates required values and PostgreSQL identifiers before it creates:

- the `AUTH_DB_NAME` database owned by `AUTH_DB_USER`;
- the `BETTING_DB_NAME` database owned by `BETTING_DB_USER`;
- the `ANALYTICS_DB_NAME` database owned by `ANALYTICS_DB_USER`.

Each service role is a non-superuser login role. Database `PUBLIC` privileges are revoked, cross-service database privileges are explicitly revoked, and only the corresponding service owner receives `CONNECT`. The default `public` schema is kept available to the database owner for later application migrations, but its privileges are not granted to `PUBLIC`.

This task creates databases and roles only. It does not create application tables, application schemas, business data, Flyway migrations, RabbitMQ resources, or Java services. Changing the initialization script does not rewrite an already initialized volume; recreate the volume intentionally when testing a clean initialization.

## Compose validation without starting containers

With `.env` present, validate the normal Compose contract:

```bash
docker compose config
```

Validate the safe versioned template directly:

```bash
docker compose --env-file .env.example config
```

The output must contain only the `postgres` service, the `suaposta-network` network, and the `postgres-data` volume. It must not contain RabbitMQ or application services.

## Start PostgreSQL and inspect health

Load local variables into the current shell for the validation commands:

```bash
set -a
. ./.env
set +a
```

Start only PostgreSQL:

```bash
docker compose up -d postgres
```

Inspect service health and the container logs:

```bash
docker compose ps
docker compose logs postgres
```

`docker compose ps` should report `healthy`. The logs should show the initialization script completing on a clean volume and PostgreSQL ready to accept connections.

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
docker compose up -d postgres

docker compose exec -T postgres \
  psql --no-psqlrc -U "$POSTGRES_SUPERUSER" -d "$POSTGRES_SUPERUSER_DB" \
  -c "SELECT datname, shobj_description(oid, 'pg_database') AS marker FROM pg_database WHERE datname = '$AUTH_DB_NAME';"
```

The three databases, their owners, and the marker must still be present after `docker compose down` followed by `docker compose up -d postgres`. `docker compose down` removes containers and the network but preserves `postgres-data`.

## Stop or intentionally reset local data

Stop the environment while preserving the named volume:

```bash
docker compose down
```

**Destructive reset — permanently deletes the local PostgreSQL data volume:**

```bash
docker compose down -v
docker compose up -d postgres
```

Use `docker compose down -v` only when local data can be discarded or recreated. The initialization script runs again after this reset because the data directory is empty.

## Decisions and limitations

- The Compose file uses the current specification and no legacy `version` property.
- Service and resource names are independent of the developer's computer.
- `container_name` is intentionally not used; Compose manages container names within the project.
- `.env.example` contains no production hostname, database URL, credential, or secret.
- PostgreSQL is the only service provisioned by Task 1.2.
- No destructive Docker command is run automatically by the implementation or validation workflow.
