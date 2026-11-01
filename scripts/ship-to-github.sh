#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
REPO_ARG="${1:-}"
VISIBILITY="${2:---public}"

if ! gh auth status >/dev/null 2>&1; then
  gh auth login --hostname github.com --web --scopes repo,workflow
fi
gh auth setup-git

if [ -z "$REPO_ARG" ]; then
  GH_USER="$(gh api user -q .login)"
  REPO_ARG="$GH_USER/tijori-android"
fi

[ -d .git ] || git init
git branch -M main
git add -A
git diff --cached --quiet || git commit -m "Tijori Vault v1.0 (100% On-Device Encrypted Password & Document Vault)"

if gh repo view "$REPO_ARG" >/dev/null 2>&1; then
  git remote get-url origin >/dev/null 2>&1 && git remote set-url origin "https://github.com/$REPO_ARG.git" || git remote add origin "https://github.com/$REPO_ARG.git"
  git push -u origin main
else
  gh repo create "$REPO_ARG" "$VISIBILITY" --source=. --remote=origin --push
fi

TIJORI_REPO="$REPO_ARG" ./scripts/setup-signing.sh
TIJORI_REPO="$REPO_ARG" ./scripts/get-play-bundle.sh
