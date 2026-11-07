# Habita

A personal workspace app, built for one person and open source so anyone can clone it and run their
own copy. It works offline first and syncs across devices through a small self-hosted server.

The first feature is a gym tracker built around progressive overload: it remembers what you lifted
last time and tells you what to lift today, so there is nothing to remember or work out at the gym.
Next to it are an expense tracker with a monthly budget and a routine checklist you reset when you
start over.

## Status

| Part           | Status                                                                    |
|----------------|---------------------------------------------------------------------------|
| Server         | Done: login, sync, progression, summaries, export                         |
| Deployment     | Done: Docker image, CI, HTTPS, server hardening, backups                  |
| Android app    | Done: home, the gym flow, expense tracker and daily routine               |
| Desktop app    | Done: the same Compose UI as Android in one window, for macOS and others  |
| iOS app        | Runs the same Compose UI as Android; not yet tested on a real iPhone      |
| Web            | Planned for later                                                         |
| Other features | Planned for later: to-do list, roadmap, month planner                     |

## How the gym feature works

1. Open the app and pick a workout. The app suggests the next one in your rotation (Upper A, Lower
   A, Upper B, Lower B), whatever day it is, so a missed day never skips a workout. Full body is a
   fallback that does not move the rotation.
2. Do the warm-up one exercise at a time: done, skip, or machine taken, which moves it to after the
   next exercise. A 2-minute rest follows.
3. Do the main workout set by set. Weighted exercises start with a warm-up set at about half of
   today's weight for 10 reps, which can be skipped and never counts toward progress. Each
   exercise shows last time's weight and reps and today's
   target, filled in so that a normal set is one tap. Log a set by tapping how it felt: Easy,
   Medium or Hard. The weight can be changed on any set, and the next set is suggested from how
   this one felt. Finishing a set starts its rest timer, and the phone buzzes when it ends.
   Logged the wrong numbers? "Undo last set" during the rest takes it back, and after the final
   set, the summary has it too, reopening the workout. Taps right after a
   step are ignored until the next step shows, so a double tap never logs twice. Finishing with
   nothing logged offers to discard instead, so an empty workout never counts.
4. At the end, see the workout summary: time spent, volume, sets, personal bests and what goes up
   next time, with charts of the time split, how the sets felt, volume over recent sessions, and
   each exercise set by set with its working weight over time.

### Progression rules

Habita uses double progression, the standard method for a fixed rep target like 3 × 12:

- Hit the target on every set, and next time the weight goes up one step.
- Miss a set, and next time you repeat the weight and try to beat last time's reps.
- Drop below 8 reps at a heavier weight, and you go back to the weight that held: a lighter one done
  well that day, or the one before a jump. One bad set long after a jump just repeats the weight.
- Make no progress for 3 sessions in a row, and the weight drops by about 10% so you can build back
  up.
- Come back after more than 3 weeks off, and the weight stays where it was. After more than 8
  weeks off, it comes back about 20% lighter (timed holds a bit shorter) to build back up safely.
- A lighter week or a comeback never goes below one step, so a light exercise never drops to 0 kg.
- Bodyweight exercises such as the plank grow their target instead, by 5 seconds at a time.

How each set felt adjusts this:

- During a workout, an Easy set suggests one step heavier for the next set (two steps when the
  reps were 3 or more over the target). A Hard set with fewer than 8 reps suggests one step
  lighter. The suggestion is filled in, with a button to keep the weight you just used.
- Hit the target on every set and every set felt easy, and next time goes up two steps.
- Hit the target on every set but every set felt hard, and next time stays at the same weight.
- Miss the target but every set felt easy, and next time still goes up one step.
- Change the weight partway through, for example 5 kg felt easy so the rest was done at 15 kg, and
  the heavier weight is the one next time builds on.

The step size defaults per exercise in the plan and can be changed in the app, for example after
switching gyms. Weights are in kg; dumbbell exercises record the weight of one dumbbell.

### Changing gyms

Local gyms don't all have the same machines. Each exercise in the plan lists alternatives, such as
a cable fly or dumbbell fly for the pec deck. Tap "Not at this gym" on the pre-start screen, or
before the first set of an exercise, and pick one. The swap is kept for every workout after, until
it's undone. "New gym: back to the plan" on the gym screen undoes every swap at once. An
alternative is an exercise of its own, so it has its own weights and history.

### Bodyweight

The gym screen takes a morning weigh-in and, about once a week, a waist measurement. It averages
each week (a week needs at least 3 weigh-ins) and says what to change, relative to what you eat
now, so the app never needs a calorie target:

- Fewer than two weeks of weigh-ins: no advice yet.
- Losing, at up to 1% of your weight a week: keep eating the same.
- Losing more than 1% a week: eat about 150 kcal a day more, to keep muscle.
- Less than 0.2 kg lost in two weeks: eat about 150 kcal a day less, or walk more. If the waist
  went down at least 0.5 cm over four weeks meanwhile, that's recomposition, so keep going.

That's for the "Lose fat" goal. The card's goal can also be "Maintain" (more than 0.5 kg up or
down in two weeks: eat about 100 kcal less or more) or "Build" (under 0.2 kg gained in two weeks:
eat about 150 kcal more; over 0.5% of your weight gained in a week: eat about 150 kcal less). The
goal syncs, and check-ins include it. If notifications are off, the card says so and offers to
turn them on.

"See your progress" on the gym screen shows the whole history: every exercise from its first
session to its latest, with its best, and bodyweight and waist since the first weigh-in.

Weighing at the gym before each workout works too, as long as it's always the same way. "Past two
weeks" lists the last 14 days: tap one to fix a typo, add a missed weigh-in or delete the day. On
Sundays, the phone shows a reminder at 9:00 to measure the waist (Android and iOS), and the home
screen's gym card says so too, unless it was measured in the last six days. The reminder is set on
the phone, so it needs no server or push service.

### Check-ins

There is no coach and no AI inside the app. Instead, about every three months, or sooner when an
exercise did no better in the last six weeks (at least four sessions) than in the six weeks before,
counting more reps at the same weight as better, the gym screen asks for a check-in. "Copy for AI"
copies one text with the period's workouts, every exercise's first, latest and best sets, how the
sets felt, the weekly bodyweight, the gym's swaps and the full plan in
`PROGRAM.md` format. Paste it into Claude or ChatGPT, ideally in a chat or project where your goal
and diet targets are saved, so they never go into this repository. If it suggests changes, it
replies with tables to paste into [program/PROGRAM.md](program/PROGRAM.md). "Copy check-in for AI"
under the backup button does the same at any time.

## The expense tracker

On Android, payments come from the bank's debit SMS (sideloaded app only, since Play doesn't allow
SMS access). Each one is saved as it arrives and a notification offers the two most used
categories; a payee seen before is sorted on its own, on any phone, because the choice travels
with the synced payments. "Scan recent SMS" catches up on this month's and last month's inbox
without adding anything twice or bringing back a payment you deleted. Cash is added by hand.

The budget is kept per month. A month can run from salary day to salary day: with "Salary day" set
to 5 on the budget screen, November is 5 November to 4 December, and the month screen shows those
dates. Saving it sets the income and limits for the month on screen and the months after it, until
one that has its own, so a raise or a new limit never changes what earlier months show. Categories
are renamed, removed and brought back on the same screen; a removed category stays on its old
payments. Export writes the month as markdown, laid out like a Notion budget page, to paste into
Notion or hand to an AI.

## The daily routine

The routine is one list of sections, each Fixed or Adjustable, shown every day in the order it's
written. Ticks stay until you tap Reset on the progress card, which asks once and then unticks
everything, on every device. For a few seconds after, Undo reset brings the ticks back. The page
itself only has the checkboxes and Reset. Edit, at the top or on any section's header, opens the
list (at that section) with a handle on every task and section: drag a task anywhere, into another
section too, and drag a section's header to move the whole section. Tap a task to rename it or a
header to switch it between Fixed and Adjustable; the bins and the add buttons live there too. A
routine from before sections (Morning, Office, Evening, Night, with Office on weekdays only) is
turned into sections on its own, keeping today's ticks.

## The workout plan

The plan lives in [program/PROGRAM.md](program/PROGRAM.md): every exercise, warm-up and workout,
with sets, target reps and rest times. The server reads it on startup and refuses to start if
anything in it is malformed, pointing at the line. An exercise that an older version of the plan
had keeps being served even if a new version drops its row, so its history never disappears.

Every exercise has a permanent id, and all history is stored against that id. You can rename an
exercise or change sets, reps and rest at any time without losing history. Each change to the file
is kept as a new program version, so old workouts still point at the plan they were done under.

To change your plan, edit the file, merge it and redeploy. The export endpoint gives you all of your
data as one JSON file, ready to review yourself or with an AI before you change anything.

## Project structure

| Path              | Purpose                                                                |
|-------------------|------------------------------------------------------------------------|
| `android`         | Android entry point                                                    |
| `ios`             | iOS entry point: an Xcode project that shows the `compose` UI          |
| `desktop`         | Desktop entry point: one window that shows the `compose` UI            |
| `compose`         | Compose Multiplatform entry point: home screen and navigation          |
| `core`            | App-wide pieces: theme, UI components, settings, server client, sync   |
| `feature/auth`    | The login screen                                                       |
| `feature/gym`     | The gym flow and its local database                                    |
| `feature/expense` | The expense tracker: payments, categories, budget and its database     |
| `feature/routine` | The routine checklist, reset by hand, and its database                 |
| `shared`          | Code for every platform and the server: models, progression, summaries |
| `server`          | Ktor server: login, sync, targets, summaries and export                |
| `program`         | The workout plan                                                       |
| `deploy`          | Docker Compose stack, server setup and backup scripts                  |

The progression rules, rotation and summary live in `shared`, so the app gives the same answers
offline that the server gives online.

## Tech stack

- Kotlin Multiplatform and Compose Multiplatform
- Ktor server with SQLite through SQLDelight
- kotlinx.serialization for the API
- Docker, Caddy and GitHub Actions for deployment

## Getting started

You need JDK 21 and, for the Android app, Android Studio with the Android SDK. For the iOS app
you need a Mac with Apple silicon and Xcode.

Clone the repository:

```sh
git clone https://github.com/sudarshanmhasrup/habita.git
cd habita
```

### Run the tests

```sh
./gradlew :shared:jvmTest :server:test :core:desktopTest :feature:gym:desktopTest \
    :feature:expense:desktopTest :feature:routine:desktopTest
```

CI also checks every database migration; the full list of checks is in [AGENTS.md](AGENTS.md).

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

The app works with no server at all: the workout plan is built into it, and every workout is kept
on the phone. Without settings, it opens straight to the home screen.

To sync with your server, turn on the login and set the server's address, once, in
`local.properties` at the repository root. The name is used in the home screen greeting and is
optional.

```properties
HABITA_LOGIN=true
HABITA_SERVER_URL=https://habita.example.com
HABITA_NAME=Sudarshan
```

All of them can also be passed as Gradle properties or environment variables, for example in CI.
Then build:

```sh
./gradlew :android:assembleDebug
```

To use a server running on your computer from the Android emulator, set
`HABITA_SERVER_URL=http://10.0.2.2:8080`. Debug builds allow plain HTTP for this; release builds
need HTTPS.

Every set and payment is saved on the phone as it is done, so the app works with no connection.
Android's automatic backup includes it, and the gym screen's "Save a backup file" button writes the
gym's records to a JSON file you choose. "Restore from a backup file" reads one back, from the app
or the server's export, keeping whatever is newer on the phone, so it never undoes newer work.
With the login on, workouts, payments, categories, the budget and the daily routine sync in the
background whenever the server is reachable, including what was saved before the login was turned
on, and the server's workout plan replaces the built-in one. How syncing works is in the
[server README](server/README.md#sync).

### Run the desktop app

The desktop app reads the same `local.properties` settings as Android and keeps its data in
`~/.habita`. Start it with:

```sh
./gradlew :desktop:run
```

To build an installer for the current system, run `./gradlew :desktop:packageDmg` on macOS,
`:desktop:packageMsi` on Windows or `:desktop:packageDeb` on Linux.

### Build the iOS app

The iOS app reads the same `local.properties` settings as Android. Open
`ios/Habita.xcodeproj` in Xcode, pick a simulator and press Run. Xcode runs Gradle to build the
shared code as the `HabitaKit` framework first, so the first build takes a few minutes. It needs
iOS 18.5 or later.

From the command line:

```sh
xcodebuild -project ios/Habita.xcodeproj -scheme Habita \
    -destination 'generic/platform=iOS Simulator' -derivedDataPath .build/ios build
```

To run on an iPhone, choose your team under Signing & Capabilities in Xcode. The simulator shares
your computer's network, so a local server is at `HABITA_SERVER_URL=http://localhost:8080`.

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