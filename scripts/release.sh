#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${AALL_KEYSTORE:?Set AALL_KEYSTORE to the persistent release keystore}"
: "${AALL_PASSWORD_FILE:?Set AALL_PASSWORD_FILE to a private file containing its password}"
version=$(node -p "require('./package.json').version")
tag="v$version"
# Version codes derive from local release time and increase across updates.
export AALL_VERSION_CODE="${AALL_VERSION_CODE:-$(date +%s)}"
pnpm typecheck
pnpm check
pnpm test --run
pnpm build:apk
signer="$ANDROID_HOME/build-tools/37.0.0/apksigner"
"$signer" sign --ks "$AALL_KEYSTORE" --ks-key-alias aall --ks-pass "file:$AALL_PASSWORD_FILE" --out artifacts/aall-signed.apk artifacts/aall.apk
"$signer" verify --verbose artifacts/aall-signed.apk
"$ANDROID_HOME/build-tools/37.0.0/zipalign" -c -P 16 4 artifacts/aall-signed.apk
if ! git rev-parse "$tag" >/dev/null 2>&1; then git tag "$tag"; fi
[[ "$(git rev-parse "$tag^{commit}")" == "$(git rev-parse HEAD)" ]] || { echo "Tag $tag belongs to another commit; bump the version." >&2; exit 1; }
git push origin HEAD "$tag"
gh release create "$tag" artifacts/aall-signed.apk --repo DanielSintimbrean/aall --title "aall $version" --generate-notes
