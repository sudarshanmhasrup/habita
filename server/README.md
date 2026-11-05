# Habita server

A single-user Ktor server. It serves the workout plan from [PROGRAM.md](../program/PROGRAM.md),
stores workout logs and expenses in SQLite and syncs them with the app.

## Configuration

| Variable               | Required | Default              | Purpose                              |
|------------------------|----------|----------------------|--------------------------------------|
| `HABITA_EMAIL`         | Yes      |                      | The only email that can log in       |
| `HABITA_PASSWORD_HASH` | Yes      |                      | bcrypt hash of the password          |
| `HABITA_DATABASE`      | No       | `.build/habita.db`   | SQLite database file                 |
| `HABITA_PROGRAM`       | No       | `program/PROGRAM.md` | Workout plan                         |
| `PORT`                 | No       | `8080`               | HTTP port                            |

There is no signup. The account exists only in these variables. Create the password hash with:

```sh
./gradlew :server:buildFatJar
java -jar .build/server/libs/server-all.jar hash-password
```

The hash contains `$` characters, so wrap it in single quotes in a shell.

## Running locally

```sh
export HABITA_EMAIL=you@example.com
export HABITA_PASSWORD_HASH='<hash>'
./gradlew :server:run
```

Relative paths resolve from the repository root.

## API

Every route except `/health` and `/auth/login` needs an `Authorization: Bearer <token>` header.

A token lasts 90 days from login and ends after 30 days unused; the app then asks for the login
again. Changing `HABITA_PASSWORD_HASH` ends every session at the next start. Login allows 5
attempts a minute from each address and 120 a minute in total, so a stranger can't lock you out.

| Route                         | Purpose                                                    |
|-------------------------------|------------------------------------------------------------|
| `GET /health`                 | Liveness check                                             |
| `POST /auth/login`            | Email and password in, token out                           |
| `POST /auth/logout`           | Revokes the token                                          |
| `POST /auth/logout-all`       | Revokes every token, on every device                       |
| `GET /program`                | The current plan and its version                           |
| `GET /workouts/next`          | The next workout in the rotation                           |
| `GET /workouts/{id}/targets`  | Today's weight and target for each exercise                |
| `GET /sessions/{id}/summary`  | End-of-workout numbers                                     |
| `POST /sync/push`             | Saves local changes; returns the cursor and refused records |
| `GET /sync/pull?since=<n>`    | Changes after cursor `n`, a page at a time (`&limit=`)     |
| `GET /export`                 | Every program version and every record as one JSON file    |

## Sync

Every feature syncs the same way. A record travels as `{ id, updatedAt, deleted, data }`, grouped
by collection name, such as `gym.sets` or `expense.expenses` (`SyncCollections` in `shared/`).

- **Last write wins.** A record replaces the stored copy when its `updatedAt` is later. On a tie
  the copy whose data sorts last wins, so every device settles on the same one (`winsOver` in
  `shared/`). The app applies the same rule, so both sides agree.
- **Bad records are refused one by one** and listed in the push reply; the rest are saved.
- **Clocks.** A time more than 5 minutes ahead of the server is pulled back, so a phone with a
  wrong clock can't make a record impossible to edit.
- **Deletes** are kept as tombstones (`deleted: true`), so they reach every device.
- **Cursor.** Every saved record gets the next server sequence number. Pull returns changes after
  the app's cursor, oldest first, with `hasMore` while there are more pages.
- **Server id.** Pull also returns `serverId`, made once per database. If it changes, for example
  after restoring an older backup, the app sends everything it has again.

The app pulls before it pushes, and keeps a local edit that hasn't reached the server yet unless
the server's copy wins over it.

### Adding a synced feature

1. Name its collections in `SyncCollections` (`shared/.../api/Sync.kt`).
2. Server: if the server never reads the data, add the names to `recordTables` in
   `server/.../sync/RecordTable.kt`. That's all. If the server needs the data (as the gym does,
   for targets), write a typed `SyncTable` like those in `GymTables.kt`.
3. App: give each local table `updated_at` and `dirty` columns, set both on every local edit, and
   call `core.sync.request()`. Implement `LocalSyncTable` (see `ExpenseSync.kt`) and register it
   with `core.sync.register` at startup.
4. Any schema change needs a migration (`.sqm`) on the side it touches.