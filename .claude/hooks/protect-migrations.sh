#!/usr/bin/env bash
# Stops an agent from changing a migration (.sqm) or schema snapshot (databases/*.db) that is
# already committed: released databases were built from them, so a change must be a new N.sqm.
# New, uncommitted files are fine. Exit code 2 blocks the edit and shows the message to the agent.
set -uo pipefail

path="$(python3 -c 'import json, sys; print(json.load(sys.stdin).get("tool_input", {}).get("file_path", ""))' 2>/dev/null)"
case "$path" in
    *.sqm | */sqldelight/databases/*.db) ;;
    *) exit 0 ;;
esac

root="${CLAUDE_PROJECT_DIR:-$(pwd)}"
relative="${path#"$root"/}"
if git -C "$root" ls-files --error-unmatch -- "$relative" >/dev/null 2>&1; then
    echo "$relative is a committed migration or schema snapshot. Add a new N.sqm migration instead of changing it." >&2
    exit 2
fi
exit 0