#!/bin/sh
set -eu
for database in user moderation scooter ride documents; do
  for migration in /migrations/"$database"/*.sql; do
    [ -f "$migration" ] || continue
    version="$(basename "$migration")"
    checksum="$(sha256sum "$migration" | cut -d ' ' -f 1)"
    {
      printf '%s\n' 'BEGIN;' 'SELECT pg_advisory_xact_lock(27102026);'
      printf '%s\n' 'CREATE TABLE IF NOT EXISTS schema_migrations(version TEXT PRIMARY KEY, checksum TEXT NOT NULL, applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW());'
      printf '%s\n' "SELECT EXISTS(SELECT 1 FROM schema_migrations WHERE version='$version') AS applied \gset"
      printf '%s\n' '\if :applied'
      printf '%s\n' "SELECT 1 / CASE WHEN checksum='$checksum' THEN 1 ELSE 0 END FROM schema_migrations WHERE version='$version';"
      printf '%s\n' '\else' "\i $migration"
      printf '%s\n' "INSERT INTO schema_migrations(version,checksum) VALUES('$version','$checksum');"
      printf '%s\n' '\endif' 'COMMIT;'
    } > /tmp/stack-migration.sql
    psql -v ON_ERROR_STOP=1 --dbname "$database" -f /tmp/stack-migration.sql
  done
done
