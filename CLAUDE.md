# CLAUDE.md

@AGENTS.md

## Claude Code

- `.claude/settings.json` asks before editing deploy files, workflows, signing, the auth code,
  the version catalog and `.claude/` itself; denies reading secrets; and blocks force-pushes and
  pushes to `main`.
- A hook blocks edits to committed migrations and schema snapshots; add a new `N.sqm` instead.