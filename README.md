# aall

Personal Android routines, edited on the phone: app blocking, focus, bedtime, grayscale and a Night Light ramp, plus days off and a short arithmetic pause. A React Native and Expo UI sits on a local Kotlin Expo module that owns persistence, scheduling and enforcement, with no account or backend. It adds personal friction, not tamper-proof enforcement. [docs/internals/design.md](docs/internals/design.md) specifies the behavior.

## Development

```sh
mise install
pnpm install --frozen-lockfile
mise run check
pnpm build:apk
```

The Android SDK needs platform 37.0, build tools 37.0.0 and NDK 27.1.12297006, and emulators need the x86_64 system image:

```sh
sdkmanager 'platforms;android-37.0' 'build-tools;37.0.0' 'ndk;27.1.12297006'
sdkmanager 'emulator' 'system-images;android-37.0;google_apis;x86_64'
```

`mise run check` runs typecheck, Vite+ and ktlint formatting and lint, and the TypeScript tests. `pnpm format` applies both formatters. The native JVM tests run inside `pnpm build:apk`. `pnpm start` and `pnpm android` give a development client; Expo Go can't load the native module.

`pnpm build:apk` builds arm64 only, so set `AALL_ABIS=x86_64` for an emulator. Set `AALL_VERSION_CODE=$(date +%s)` too, or Android refuses to install it over a newer build. The APK is signed with Expo's development key, so it can't update a release install: switching keys means uninstalling, and uninstalling deletes the settings.

## Phone setup

Phone setup in the app grants accessibility, DND, exact alarms and notifications. A sideloaded app may need **Allow restricted settings** in Android's app info before accessibility can be turned on.

Grayscale and Night Light need a one-time grant:

```sh
adb shell pm grant dev.danields.aall android.permission.WRITE_SECURE_SETTINGS
```

## Releases

Releases are built and signed locally and published to GitHub Releases at https://github.com/DanielSintimbrean/aall. GitHub Actions isn't used. On the phone, Obtainium follows that repository as a GitHub source.

1. Bump the version in `package.json` and `app.config.ts`, and commit.
2. Run `pnpm release`. It runs the checks, builds the APK, signs it with `~/.local/share/aall/signing/aall.jks` and the password file beside it, verifies the signature and alignment, tags the commit and publishes the release. `AALL_KEYSTORE` and `AALL_PASSWORD_FILE` override the paths.

Version codes default to Unix seconds, so they always increase. Each tag is released once.

Keep a private backup of the key and its password. Android rejects an update signed with a different key, so a lost or recreated key means uninstalling, which deletes the settings.
