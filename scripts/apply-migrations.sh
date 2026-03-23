#!/bin/bash
set -euo pipefail

if [ -z "${SUPABASE_DB_URL:-}" ]; then
  echo "ERROR: Set SUPABASE_DB_URL first"
  echo "Format: postgres://postgres.[project-ref]:[password]@aws-0-eu-central-1.pooler.supabase.com:6543/postgres"
  exit 1
fi

MIGRATIONS_DIR="$(dirname "$0")/../supabase/migrations"

for f in "${MIGRATIONS_DIR}"/*.sql; do
  echo "Applying: $(basename "$f")"
  psql "${SUPABASE_DB_URL}" -f "$f"
done

echo "All migrations applied."
