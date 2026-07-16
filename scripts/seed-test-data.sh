#!/usr/bin/env bash
# Bơm dữ liệu test bổ sung (scripts/test-data-seed.sql) vào MySQL đang chạy
# trong docker compose (service "mysql"). Không đụng tới seed gốc data.sql.
#
# Cách dùng:
#   ./scripts/seed-test-data.sh          # seed du lieu [TEST]
#   ./scripts/seed-test-data.sh --clean  # chi xoa du lieu [TEST]

set -euo pipefail

SERVICE="${SERVICE:-mysql}"
DATABASE="${DATABASE:-roomfinder}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-root}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$REPO_ROOT"

if ! docker compose ps --status running --services | grep -qx "$SERVICE"; then
  echo "Container '$SERVICE' chua chay. Dang start..."
  docker compose up -d "$SERVICE"
  sleep 3
fi

if [[ "${1:-}" == "--clean" ]]; then
  echo "DELETE FROM room WHERE title LIKE '[TEST]%'; DELETE FROM poi WHERE name LIKE '[TEST]%';" \
    | docker compose exec -T "$SERVICE" mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DATABASE"
  echo "Da xoa du lieu [TEST]."
else
  docker compose exec -T "$SERVICE" mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DATABASE" < "$SCRIPT_DIR/test-data-seed.sql"
  echo "Da them du lieu test vao bang room/poi."
fi
