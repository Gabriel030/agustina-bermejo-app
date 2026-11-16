#!/usr/bin/env bash
[ "$(uname)" = "Darwin" ] || exit 0
common=$(git rev-parse --path-format=absolute --git-common-dir 2>/dev/null) || exit 0
repo=$(dirname "$common")
path="$repo/.claude/worktrees/index-develop"
if [ ! -d "$path" ]; then
  git -C "$repo" fetch -q origin develop 2>/dev/null
  if git -C "$repo" worktree add -q --detach "$path" origin/develop 2>/dev/null; then
    echo "code-index: worktree creado en origin/develop" >&2
  else
    echo "code-index: no se pudo crear el worktree" >&2
  fi
else
  if git -C "$path" fetch -q origin develop 2>/dev/null && git -C "$path" checkout -q --detach origin/develop 2>/dev/null; then
    echo "code-index: actualizado a origin/develop" >&2
  else
    echo "code-index: no se pudo actualizar" >&2
  fi
fi
exit 0
