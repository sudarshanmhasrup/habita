# CLAUDE.md

Project rules live in [Coding-guidelines.md](Coding-guidelines.md). Read it before changing
anything.

## Branching Strategy

- `main` is the release branch. Releases are tags on `main`.
- Never commit directly to `main`, and never force-push to it.
- Work on a `type/branch-name` branch (lowercase, kebab-case). Allowed types: `feature`, `fix`,
  `refactor`, `build`, `ci`, `documentation`, `test`, `chore`.
- Branch off the latest `main` and open the pull request into `main`.
- Write commit messages as `type: message`, in lowercase and the imperative.
- Merge with "Rebase and Merge" only, so `main` stays linear. Delete the branch afterwards.
- Keep each branch focused on one change.
- See "Branching Strategy" in [Coding-guidelines.md](Coding-guidelines.md) for the full workflow.