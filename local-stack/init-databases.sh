#!/bin/sh
set -eu
for database in user moderation scooter ride documents; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -c "CREATE DATABASE \"$database\";"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$database" -f "/schemas/$database.sql"
done
