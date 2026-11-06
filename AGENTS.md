# AGENTS.md

Habita is a single-user personal app: a Ktor server (`server/`) with SQLite, a Kotlin
Multiplatform core (`shared/`, `core/`), Compose screens (`compose/`, `feature/*`) and Android
and iOS entry points (`android/`, `ios/`). It holds the owner's real workout and money data, and
the server is on the public internet. Full rules: [CODING-GUIDELINES.md](CODING-GUIDELINES.md).

## Check your work

Run what CI runs before saying a change is done. Build output goes to `.build/`, not `build/`.

```sh
./gradlew :shared:jvmTest :server:test :core:desktopTest :feature:gym:desktopTest \
    :feature:expense:desktopTest :feature:routine:desktopTest :android:assembleDebug
./gradlew :server:verifySqlDelightMigration :core:verifySqlDelightMigration \
    :feature:gym:verifySqlDelightMigration :feature:expense:verifySqlDelightMigration \
    :feature:routine:verifySqlDelightMigration
```

iOS (Mac only): `./gradlew :compose:linkDebugFrameworkIosSimulatorArm64`, then build
`ios/Habita.xcodeproj`.

## Rules that protect the data

- Every server route goes inside `authenticate(SESSION_AUTH) { }` except `GET /health` and
  `POST /auth/login`. `RouteAuthTest` fails otherwise; never add to its public list without
  saying why.
- Never log or store a raw token, password, hash or `.env` value. The server keeps only token
  hashes.
- A schema change is a new `N.sqm` migration next to the `.sq` files. Never edit an existing
  `.sqm` or a `databases/*.db` snapshot; the migration check compares against them.
- Synced records change additively: new fields get defaults, nothing is renamed or removed. A
  server that understands the new shape deploys before the app that sends it.
- New features sync through the shared engine; follow "Adding a synced feature" in
  [server/README.md](server/README.md#adding-a-synced-feature).
- Colours come from the theme (`HabitaTheme.colors`, built by `Accent.palette`); never hardcode
  one in a screen.
- No new dependency, Gradle plugin or GitHub Action without asking first.
- Never touch the signing key, `android/signing/`, `scripts/sign-apk.sh`, `deploy/` or the
  workflows without asking.

## When a test fails

Fix the code, not the test. If a test looks wrong, stop and say so instead of changing,
skipping or deleting it.

## Git

- Branch off `main` as `type/short-name`; types: feature, fix, refactor, setup, build, ci, docs,
  test, chore. One change per branch.
- Commit messages are `type: message`, lowercase, imperative. No co-author or "generated with"
  lines.
- Never commit to or force-push `main`. Merges are rebase merges.

## Files

- End every file on its last line, with no trailing newline.
- Markdown files are named in uppercase (`README.md`) and wrapped at 100 columns.
- YAML is indented four spaces, and every `name:` value is in double quotes.

## Always for a human to review

Anything under `server/.../auth/`, `Application.kt`'s plugins and routing, sync code, `.sqm`
files and `databases/`, `gradle/libs.versions.toml`, `deploy/`, `.github/`, signing files,
`.claude/`, and any diff that edits or deletes tests.