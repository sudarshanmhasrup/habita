# Habita

A personal workspace app, built for one person and open source so anyone can clone it and run their
own copy. It works offline first and syncs across devices through a small self-hosted server.

The first feature is a gym tracker built around progressive overload: it remembers what you lifted
last time and tells you what to lift today, so there is nothing to remember or work out at the gym.

## Status

| Part           | Status                                                                  |
|----------------|-------------------------------------------------------------------------|
| Server         | Done: login, sync, progression, summaries, export                       |
| Deployment     | Done: Docker image, CI, HTTPS, server hardening, backups                |
| Android app    | In progress: project setup only, no screens yet                         |
| Desktop app    | Planned, sharing the Compose Multiplatform UI with Android              |
| iOS and web    | Planned for later                                                       |
| Other features | Planned for later: expense tracker, to-do list, roadmap, month planner  |

## How the gym feature works

1. Open the app and pick a workout. The app suggests the next one in your rotation (Upper A, Lower
   A, Upper B, Lower B), whatever day it is, so a missed day never skips a workout. Full body is a
   fallback that does not move the rotation.
2. Do the warm-up one exercise at a time: done, skip, or machine taken, which moves it to after the
   next exercise. A 2-minute rest follows.
3. Do the main workout set by set. Each exercise shows last time's weight and reps and today's
   target, filled in so that a normal set is one tap. Finishing a set starts its rest timer.
4. At the end, see the workout summary: time spent, volume compared with last time, exercises that
   progressed, personal bests, sets completed and skipped, and what goes up next time.

### Progression rules

Habita uses double progression, the standard method for a fixed rep target like 3 × 12:

- Hit the target on every set, and next time the weight goes up one step.
- Miss a set, and next time you repeat the weight and try to beat last time's reps.
- Drop below 8 reps right after a weight jump, and you go back to the previous weight.
- Make no progress for 3 sessions in a row, and the weight drops by about 10% so you can build back
  up.
- Come back after more than 3 weeks off, and the weight stays where it was.
- Bodyweight exercises such as the plank grow their target instead, by 5 seconds at a time.

The step size defaults per exercise in the plan and can be changed in the app, for example after
switching gyms. Weights are in kg; dumbbell exercises record the weight of one dumbbell.

## The workout plan

The plan lives in [program/PROGRAM.md](program/PROGRAM.md): every exercise, warm-up and workout,
with sets, target reps and rest times. The server reads it on startup and refuses to start if
anything in it is malformed, pointing at the line.

Every exercise has a permanent id, and all history is stored against that id. You can rename an
exercise or change sets, reps and rest at any time without losing history. Each change to the file
is kept as a new program version, so old workouts still point at the plan they were done under.

To change your plan, edit the file, merge it and redeploy. The export endpoint gives you all of your
data as one JSON file, ready to review yourself or with an AI before you change anything.

## Project structure

| Path             | Purpose                                                                |
|------------------|------------------------------------------------------------------------|
| `android`        | Android entry point                                                    |
| `compose`        | Compose Multiplatform UI shared by Android and desktop                 |
| `shared`         | Code for every platform and the server: models, progression, summaries |
| `server`         | Ktor server: login, sync, targets, summaries and export                |
| `program`        | The workout plan                                                       |
| `deploy`         | Docker Compose stack, server setup and backup scripts                  |
| `feature/<name>` | One module per feature, added as features are built                    |

The progression rules, rotation and summary live in `shared`, so the app gives the same answers
offline that the server gives online.

## Tech stack

- Kotlin Multiplatform and Compose Multiplatform
- Ktor server with SQLite through SQLDelight
- kotlinx.serialization for the API
- Docker, Caddy and GitHub Actions for deployment

## Getting started

You need JDK 21 and, for the Android app, Android Studio with the Android SDK.

Clone the repository:

```sh
git clone https://github.com/sudarshanmhasrup/habita.git
cd habita
```

### Run the tests

```sh
./gradlew :shared:jvmTest :server:test
```

### Run the server locally

The server has no signup. The one account is set by an email and a bcrypt password hash in
environment variables. Create the hash first:

```sh
./gradlew :server:buildFatJar
java -jar .build/server/libs/server-all.jar hash-password
```

Then start the server:

```sh
export HABITA_EMAIL=you@example.com
export HABITA_PASSWORD_HASH='<hash>'
./gradlew :server:run
```

It listens on port 8080. Check it with `curl localhost:8080/health`. The full configuration and API
are in the [server README](server/README.md).

### Build the Android app

```sh
./gradlew :android:assembleDebug
```

## Deploying your own server

A small Linux server, from about 4 euros a month, is enough. The
[deployment guide](deploy/README.md) takes you from a fresh Ubuntu server to HTTPS in seven steps,
including firewall and SSH hardening, automatic security updates and nightly backups. Every merge to
`main` publishes a new server image through GitHub Actions; the server pulls it with one command.

## Contributing

Habita is built for personal use and keeps its scope small on purpose, so new features are unlikely
to be accepted. Bug fixes are welcome. Read [CODING-GUIDELINES.md](CODING-GUIDELINES.md) for the
branch, commit and pull request workflow before opening one.

## License

Licensed under the [Apache License 2.0](LICENSE).