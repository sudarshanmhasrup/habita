#!/usr/bin/env bash
# Backs up the Habita database. Cron runs it nightly as root (see setup-server.sh).
#
# Keeps 30 days of compressed copies in backups/. When HABITA_BACKUP_REMOTE is set in .env, each
# copy also goes to that rclone remote, so a lost server does not mean lost history.
set -euo pipefail
cd "$(dirname "$0")"

DATABASE=data/habita.db
BACKUPS=backups
KEEP_DAYS=30

if [ ! -f "$DATABASE" ]; then
    echo "$(date -u +%FT%TZ) No database yet, nothing to back up."
    exit 0
fi

name="habita-$(date -u +%Y-%m-%dT%H%M%SZ).db"

# .backup takes a consistent copy even while the server is writing.
sqlite3 "$DATABASE" ".backup '$BACKUPS/$name'"
if [ "$(sqlite3 "$BACKUPS/$name" 'PRAGMA integrity_check;')" != "ok" ]; then
    echo "$(date -u +%FT%TZ) Backup $name failed its integrity check." >&2
    rm -f "$BACKUPS/$name"
    exit 1
fi
gzip "$BACKUPS/$name"
find "$BACKUPS" -name 'habita-*.db.gz' -mtime +"$KEEP_DAYS" -delete

remote="$(grep -E '^HABITA_BACKUP_REMOTE=' .env 2>/dev/null | cut -d= -f2- | tr -d "'\"" || true)"
if [ -n "$remote" ]; then
    rclone copy "$BACKUPS/$name.gz" "$remote"
    echo "$(date -u +%FT%TZ) Backed up $name.gz and copied it to $remote."
else
    echo "$(date -u +%FT%TZ) Backed up $name.gz (server only; set HABITA_BACKUP_REMOTE to copy it off)."
fi