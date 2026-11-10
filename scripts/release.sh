#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
AALL_KEYSTORE="${AALL_KEYSTORE:-${XDG_DATA_HOME:-$HOME/.local/share}/aall/signing/aall.jks}"
AALL_PASSWORD_FILE="${AALL_PASSWORD_FILE:-${XDG_DATA_HOME:-$HOME/.local/share}/aall/signing/password}"
[[ -f "$AALL_KEYSTORE" && -f "$AALL_PASSWORD_FILE" ]] || { echo "Provide AALL_KEYSTORE and AALL_PASSWORD_FILE, or set up local signing files." >&2; exit 1; }
[[ -z "$(git status --porcelain)" ]] || { echo "Commit changes before publishing a release." >&2; exit 1; }
version=$(node -p "require('./package.json').version")
tag="v$version"
export AALL_VERSION_CODE="${AALL_VERSION_CODE:-$(date +%s)}"
pnpm typecheck
pnpm check
pnpm test --run
mise run test:kotlin
pnpm build:apk
signer="$ANDROID_HOME/build-tools/37.0.0/apksigner"
"$signer" sign --ks "$AALL_KEYSTORE" --ks-key-alias aall --ks-pass "file:$AALL_PASSWORD_FILE" --out artifacts/aall-signed.apk artifacts/aall.apk
"$signer" verify --verbose artifacts/aall-signed.apk
"$ANDROID_HOME/build-tools/37.0.0/zipalign" -c -P 16 4 artifacts/aall-signed.apk
if ! git rev-parse "$tag" >/dev/null 2>&1; then git tag "$tag"; fi
[[ "$(git rev-parse "$tag^{commit}")" == "$(git rev-parse HEAD)" ]] || { echo "Tag $tag belongs to another commit; bump the version." >&2; exit 1; }
git push origin HEAD "$tag"
gh release create "$tag" artifacts/aall-signed.apk --repo DanielSintimbrean/aall --title "aall $version" --generate-notes
