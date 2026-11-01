# CLAUDE.md

Project rules live in [CODING-GUIDELINES.md](CODING-GUIDELINES.md). Read it before changing
anything.

## Branching Strategy

- `main` is the release branch. Releases are tags on `main`.
- Never commit directly to `main`, and never force-push to it.
- Work on a `type/branch-name` branch (lowercase, kebab-case). Allowed types: `feature`, `fix`,
  `refactor`, `setup`, `build`, `ci`, `docs`, `test`, `chore`.
- Branch off the latest `main` and open the pull request into `main`.
- Write commit messages as `type: message`, in lowercase and the imperative.
- Merge with "Rebase and Merge" only, so `main` stays linear. Delete the branch afterwards.
- Keep each branch focused on one change.
- See "Branching Strategy" in [CODING-GUIDELINES.md](CODING-GUIDELINES.md) for the full workflow.

### Markdown files

- Name every markdown file in uppercase, with words separated by hyphens (for example
  `CODING-GUIDELINES.md`).
- Wrap markdown at 100 columns, the same width as code.
- See "Markdown files" in [CODING-GUIDELINES.md](CODING-GUIDELINES.md).
