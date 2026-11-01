#!/usr/bin/env bash
set -euo pipefail

ZIP="${1:?Usage: ./update-from-zip.sh path/to/tijori-android-latest.zip}"
REPO="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

unzip -q "$ZIP" -d "$TMP"
SRC="$TMP/tijori-android"
[ -d "$SRC" ] || { echo "The zip doesn't contain a tijori-android folder."; exit 1; }
rm -rf "$SRC/.git" "$SRC/dist" "$SRC/.gradle" "$SRC/app/build"

keep='^\./(\.git/|\.gradle/|build/|app/build/|\.idea/|dist/|local\.properties$|secrets\.env$)|\.jks$|\.keystore$|\.b64$'

( cd "$REPO" && find . -type f | grep -Ev "$keep" | sort ) > "$TMP/old.txt"
( cd "$SRC" && find . -type f | sort ) > "$TMP/new.txt"
removed=0
while IFS= read -r f; do
  rm -f "$REPO/$f"; echo "removed  ${f#./}"; removed=$((removed + 1))
done < <(comm -23 "$TMP/old.txt" "$TMP/new.txt")

cp -a "$SRC/." "$REPO/"
find "$REPO" -type d -empty -not -path "*/.git/*" -delete 2>/dev/null || true

cd "$REPO"
git add -A
echo
echo "Removed $removed old file(s). Changes ready to commit:"
git status --short
echo
echo "Next:  git commit -m \"Update Tijori app\" && git push"
