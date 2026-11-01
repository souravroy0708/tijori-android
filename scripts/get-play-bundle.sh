#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

WORKFLOW="build-apk.yml"
OUT="dist"
MODE="${1:-build}"

say()  { printf '\n\033[1m%s\033[0m\n' "$*"; }
fail() { printf '\n\033[31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

resolve_repo() {
  local url
  REPO="${TIJORI_REPO:-}"
  if [ -z "$REPO" ]; then
    url="$(git remote get-url origin 2>/dev/null || true)"
    REPO="$(printf '%s' "$url" | sed -E 's#^(https?://([^@/]+@)?github\.com/|git@github\.com:|ssh://git@github\.com/)##; s#\.git$##; s#/$##')"
  fi
  case "$REPO" in
    */*) ;;
    *) fail "Could not find the GitHub repo from 'git remote'. Set TIJORI_REPO=souravroy0708/tijori-android $0" ;;
  esac
}

command -v gh >/dev/null 2>&1 || fail "GitHub CLI 'gh' is not installed."
gh auth status >/dev/null 2>&1 || fail "Not logged in. Run: gh auth login"
resolve_repo

latest_id() { gh run list --repo "$REPO" --workflow "$WORKFLOW" --limit 1 --json databaseId -q '.[0].databaseId // empty' 2>/dev/null || true; }

if [ "$MODE" = "--latest" ]; then
  RUN_ID="$(gh run list --repo "$REPO" --workflow "$WORKFLOW" --status success --limit 1 --json databaseId -q '.[0].databaseId // empty')"
  [ -n "$RUN_ID" ] || fail "No successful build found yet."
  say "Using latest successful build #$RUN_ID"
else
  PREV="$(latest_id)"
  say "Starting a build on GitHub ($REPO, branch main)..."
  gh workflow run "$WORKFLOW" --repo "$REPO" --ref main
  RUN_ID=""
  for _ in $(seq 1 30); do
    sleep 3
    NOW="$(latest_id)"
    if [ -n "$NOW" ] && [ "$NOW" != "$PREV" ]; then RUN_ID="$NOW"; break; fi
  done
  [ -n "$RUN_ID" ] || fail "The build did not start. Check the Actions tab on GitHub."
  say "Waiting for build #$RUN_ID (takes about 2 to 4 minutes)..."
  if ! gh run watch "$RUN_ID" --repo "$REPO" --exit-status >/dev/null; then
    echo "Build failed. Log: https://github.com/$REPO/actions/runs/$RUN_ID" >&2
    exit 1
  fi
fi

say "Downloading build #$RUN_ID into ./$OUT ..."
rm -rf "$OUT/run-$RUN_ID"
gh run download "$RUN_ID" --repo "$REPO" --dir "$OUT/run-$RUN_ID"
find "$OUT/run-$RUN_ID" -type f \( -name '*.aab' -o -name '*.apk' \) -print
