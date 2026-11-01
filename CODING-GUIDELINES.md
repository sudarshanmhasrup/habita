# Coding guidelines

## Branching Strategy

### Main branch

`main` is the release branch. It always builds and holds the latest reviewed work. Every release is
a tag on `main` (for example `v1.0.0`).

- Never commit directly to `main`. It changes only through pull requests.
- Never force-push to `main`.

### Working branches

Name every working branch `type/branch-name`:

- `type` is one of the values below.
- `branch-name` is short, lowercase and kebab-case, and describes the change, not the author or an
  issue number alone.

| Type            | Use for                                                              |
|-----------------|----------------------------------------------------------------------|
| `feature`       | A new feature                                                        |
| `fix`           | A bug fix, including urgent fixes to a released version              |
| `refactor`      | A change that neither adds a feature nor fixes a bug                 |
| `setup`         | Project and module setup: new modules, source sets, plugins, tooling |
| `build`         | Changes to the build: Gradle, dependencies, versions                 |
| `ci`            | CI configuration                                                     |
| `docs`          | Documentation only                                                   |
| `test`          | Adding or fixing tests                                               |
| `chore`         | Anything else that does not touch app behaviour                      |

Examples: `feature/habit-streaks`, `fix/splash-icon-size`, `build/version-catalog-cleanup`.

### Workflow

1. Branch off the latest `main`.
2. Keep the branch focused on one change and short-lived.
3. Write commit messages as `type: message`, using the same types as branches, in lowercase and the
   imperative (for example `fix: correct the splash icon size`).
4. Open a pull request into `main`. A contributor's pull request needs a maintainer's approval
   before it is merged.
5. Merge with GitHub's **Rebase and Merge**. Do not use "Create a merge commit" or "Squash and
   merge", so `main` keeps a linear history.
6. Delete the branch after it is merged.
7. If the pull request conflicts with `main`, rebase your branch on `main`, resolve the conflicts
   and force-push your own branch with `--force-with-lease`.

### Releases

Tag the release commit on `main` with `vX.Y.Z`. If a release ever needs to be stabilised while new
work continues, cut a `release/x.y` branch from `main` at that point.

### Contributing from a fork

Fork the repository, then follow the same steps in your fork: branch off `main` and open the pull
request against `main`.

## Markdown files

- Name every markdown file in uppercase, with words separated by hyphens: `README.md`, `CLAUDE.md`,
  `CODING-GUIDELINES.md`, `CONTRIBUTING.md`. Update any links when you rename one.
- Wrap markdown at 100 columns, the same width as code. Tables and long links are the only
  exception, because they cannot be wrapped.

## Files

End every file in the codebase on its last line of content, with no trailing newline or blank
lines after it. This applies to every file type: Kotlin, Gradle, XML, properties, TOML, markdown
and the rest. Turn off "Ensure every saved file ends with a line break" in your IDE so it does not
add one back.