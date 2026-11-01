#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

DIR="${TIJORI_SIGNING_DIR:-$HOME/tijori-signing}"
KEYSTORE="$DIR/tijori-release.jks"
SECRETS="$DIR/secrets.env"
ALIAS="tijorirelease"
DNAME="${TIJORI_DNAME:-CN=TijoriVault, OU=Security, O=Tijori, L=Hyderabad, ST=Telangana, C=IN}"

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

for tool in keytool gh base64 openssl; do
  command -v "$tool" >/dev/null 2>&1 || fail "'$tool' is not installed. Try: sudo apt install default-jdk-headless gh openssl"
done

if ! gh auth status >/dev/null 2>&1; then
  say "Logging in to GitHub..."
  gh auth login --hostname github.com --web --scopes repo,workflow
fi
resolve_repo
say "Repository: $REPO"

mkdir -p "$DIR"
chmod 700 "$DIR"

if [ -f "$KEYSTORE" ] && [ -f "$SECRETS" ]; then
  say "Reusing existing key in $DIR"
  . "$SECRETS"
else
  say "Creating a new signing key..."
  STORE_PASS="$(openssl rand -base64 24 | tr -d '/+=\n' | cut -c1-24)"
  keytool -genkeypair -keystore "$KEYSTORE" -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STORE_PASS" -keypass "$STORE_PASS" \
    -dname "$DNAME" >/dev/null
  umask 077
  {
    echo "STORE_PASS='$STORE_PASS'"
    echo "KEY_ALIAS='$ALIAS'"
  } > "$SECRETS"
  chmod 600 "$SECRETS" "$KEYSTORE"
fi

say "Uploading the 4 secrets to $REPO ..."
base64 < "$KEYSTORE" | tr -d '\n' | gh secret set TIJORI_KEYSTORE_BASE64   --repo "$REPO"
printf '%s' "$STORE_PASS"          | gh secret set TIJORI_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "${KEY_ALIAS:-$ALIAS}" | gh secret set TIJORI_KEY_ALIAS         --repo "$REPO"
printf '%s' "$STORE_PASS"          | gh secret set TIJORI_KEY_PASSWORD      --repo "$REPO"

say "Done. Key fingerprint (SHA-256):"
keytool -list -keystore "$KEYSTORE" -storepass "$STORE_PASS" 2>/dev/null | grep -i "fingerprint" || true
echo "Next: ./scripts/get-play-bundle.sh"
