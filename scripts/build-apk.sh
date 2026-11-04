#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
pnpm exec expo prebuild --platform android --no-install
cd android
./gradlew :app:assembleRelease :aall-automation:testDebugUnitTest -PreactNativeArchitectures=arm64-v8a --console=plain
cd ..
mkdir -p artifacts
cp android/app/build/outputs/apk/release/app-release.apk artifacts/aall.apk
printf 'APK: %s/artifacts/aall.apk\n' "$PWD"
