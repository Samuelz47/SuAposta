#!/usr/bin/env bash

set -Eeuo pipefail

readonly IDENTIFIER_PATTERN='^[a-z_][a-z0-9_]*$'

fail() {
  printf 'PostgreSQL initialization error: %s\n' "$1" >&2
  exit 1
}

require_non_empty() {
  local variable_name="$1"

  if [[ -z "${!variable_name:-}" ]]; then
    fail "${variable_name} must be set and must not be empty"
  fi
}

validate_identifier() {
  local variable_name="$1"
  local value="${!variable_name}"

  if [[ ! "$value" =~ $IDENTIFIER_PATTERN ]]; then
    fail "${variable_name} must be a lowercase PostgreSQL identifier matching ${IDENTIFIER_PATTERN}"
  fi
}

validate_password() {
  local variable_name="$1"
  local value="${!variable_name}"

  require_non_empty "$variable_name"

  if [[ "$value" == *$'\n'* || "$value" == *$'\r'* ]]; then
    fail "${variable_name} must not contain newline characters"
  fi
}

for variable_name in \
  POSTGRES_USER \
  POSTGRES_PASSWORD \
  POSTGRES_DB \
  POSTGRES_PORT \
  AUTH_DB_NAME \
  AUTH_DB_USER \
  AUTH_DB_PASSWORD \
  BETTING_DB_NAME \
  BETTING_DB_USER \
  BETTING_DB_PASSWORD \
  ANALYTICS_DB_NAME \
  ANALYTICS_DB_USER \
  ANALYTICS_DB_PASSWORD; do
  require_non_empty "$variable_name"
done

for variable_name in \
  POSTGRES_USER \
  POSTGRES_DB \
  AUTH_DB_NAME \
  AUTH_DB_USER \
  BETTING_DB_NAME \
  BETTING_DB_USER \
  ANALYTICS_DB_NAME \
  ANALYTICS_DB_USER; do
  validate_identifier "$variable_name"
done

if [[ ! "$POSTGRES_PORT" =~ ^[0-9]+$ ]] || (( POSTGRES_PORT < 1 || POSTGRES_PORT > 65535 )); then
  fail 'POSTGRES_PORT must be an integer between 1 and 65535'
fi

for variable_name in \
  POSTGRES_PASSWORD \
  AUTH_DB_PASSWORD \
  BETTING_DB_PASSWORD \
  ANALYTICS_DB_PASSWORD; do
  validate_password "$variable_name"
done

if [[ "$AUTH_DB_NAME" == "$BETTING_DB_NAME" || \
      "$AUTH_DB_NAME" == "$ANALYTICS_DB_NAME" || \
      "$BETTING_DB_NAME" == "$ANALYTICS_DB_NAME" ]]; then
  fail 'AUTH_DB_NAME, BETTING_DB_NAME and ANALYTICS_DB_NAME must be distinct'
fi

if [[ "$AUTH_DB_USER" == "$BETTING_DB_USER" || \
      "$AUTH_DB_USER" == "$ANALYTICS_DB_USER" || \
      "$BETTING_DB_USER" == "$ANALYTICS_DB_USER" ]]; then
  fail 'AUTH_DB_USER, BETTING_DB_USER and ANALYTICS_DB_USER must be distinct'
fi

if [[ "$POSTGRES_USER" == "$AUTH_DB_USER" || \
      "$POSTGRES_USER" == "$BETTING_DB_USER" || \
      "$POSTGRES_USER" == "$ANALYTICS_DB_USER" ]]; then
  fail 'POSTGRES_USER must be different from every service database user'
fi

if [[ "$POSTGRES_DB" == "$AUTH_DB_NAME" || \
      "$POSTGRES_DB" == "$BETTING_DB_NAME" || \
      "$POSTGRES_DB" == "$ANALYTICS_DB_NAME" ]]; then
  fail 'POSTGRES_DB must be different from every service database name'
fi

run_psql() {
  psql \
    --no-psqlrc \
    --set=ON_ERROR_STOP=1 \
    --username="$POSTGRES_USER" \
    "$@"
}

run_psql \
  --dbname="$POSTGRES_DB" \
  -v "auth_db_password=$AUTH_DB_PASSWORD" \
  -v "betting_db_password=$BETTING_DB_PASSWORD" \
  -v "analytics_db_password=$ANALYTICS_DB_PASSWORD" <<SQL
CREATE ROLE "$AUTH_DB_USER"
  LOGIN
  NOSUPERUSER
  NOCREATEDB
  NOCREATEROLE
  NOREPLICATION
  NOBYPASSRLS
  PASSWORD :'auth_db_password';

CREATE ROLE "$BETTING_DB_USER"
  LOGIN
  NOSUPERUSER
  NOCREATEDB
  NOCREATEROLE
  NOREPLICATION
  NOBYPASSRLS
  PASSWORD :'betting_db_password';

CREATE ROLE "$ANALYTICS_DB_USER"
  LOGIN
  NOSUPERUSER
  NOCREATEDB
  NOCREATEROLE
  NOREPLICATION
  NOBYPASSRLS
  PASSWORD :'analytics_db_password';

CREATE DATABASE "$AUTH_DB_NAME" OWNER "$AUTH_DB_USER" TEMPLATE template0;
CREATE DATABASE "$BETTING_DB_NAME" OWNER "$BETTING_DB_USER" TEMPLATE template0;
CREATE DATABASE "$ANALYTICS_DB_NAME" OWNER "$ANALYTICS_DB_USER" TEMPLATE template0;

REVOKE ALL PRIVILEGES ON DATABASE "$AUTH_DB_NAME" FROM PUBLIC;
REVOKE ALL PRIVILEGES ON DATABASE "$AUTH_DB_NAME" FROM "$BETTING_DB_USER", "$ANALYTICS_DB_USER";
GRANT CONNECT ON DATABASE "$AUTH_DB_NAME" TO "$AUTH_DB_USER";

REVOKE ALL PRIVILEGES ON DATABASE "$BETTING_DB_NAME" FROM PUBLIC;
REVOKE ALL PRIVILEGES ON DATABASE "$BETTING_DB_NAME" FROM "$AUTH_DB_USER", "$ANALYTICS_DB_USER";
GRANT CONNECT ON DATABASE "$BETTING_DB_NAME" TO "$BETTING_DB_USER";

REVOKE ALL PRIVILEGES ON DATABASE "$ANALYTICS_DB_NAME" FROM PUBLIC;
REVOKE ALL PRIVILEGES ON DATABASE "$ANALYTICS_DB_NAME" FROM "$AUTH_DB_USER", "$BETTING_DB_USER";
GRANT CONNECT ON DATABASE "$ANALYTICS_DB_NAME" TO "$ANALYTICS_DB_USER";
SQL

while IFS='|' read -r database_name owner_name; do
  run_psql --dbname="$database_name" <<SQL
REVOKE ALL PRIVILEGES ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO "$owner_name";
SQL
done <<EOF
$AUTH_DB_NAME|$AUTH_DB_USER
$BETTING_DB_NAME|$BETTING_DB_USER
$ANALYTICS_DB_NAME|$ANALYTICS_DB_USER
EOF

printf 'PostgreSQL initialization complete: service databases and owners created.\n'
