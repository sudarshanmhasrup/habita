# Habita server

A single-user Ktor server. It serves the workout plan from [PROGRAM.md](../program/PROGRAM.md),
stores workout logs in SQLite and syncs them with the app.

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

| Route                         | Purpose                                                    |
|-------------------------------|------------------------------------------------------------|
| `GET /health`                 | Liveness check                                             |
| `POST /auth/login`            | Email and password in, token out. Limited to 5 per minute. |
| `POST /auth/logout`           | Revokes the token                                          |
| `GET /program`                | The current plan and its version                           |
| `GET /workouts/next`          | The next workout in the rotation                           |
| `GET /workouts/{id}/targets`  | Today's weight and target for each exercise                |
| `GET /sessions/{id}/summary`  | End-of-workout numbers                                     |
| `POST /sync/push`             | Saves local changes, returns the new cursor                |
| `GET /sync/pull?since=<n>`    | Everything changed after cursor `n`                        |
| `GET /export`                 | Every program version and every log as one JSON file       |