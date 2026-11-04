# aall

Personal Android routines with on-phone editing. React Native, Expo and Uniwind provide the UI; a local Kotlin Expo module owns persistence, scheduling and enforcement. No account or backend. [DESIGN.md](DESIGN.md) specifies the behavior.

All routines start disabled. Today shows scheduled activity, missing permissions and any "only today" hours in effect. Saving weekly settings ends that routine's "only today" change. Editors configure allowed hours for app access, active grayscale hours, bedtime, weekdays and per-routine app lists. Focus defaults to 50 minutes. An arithmetic challenge pauses every restriction for 15 minutes. Days off suppress all restrictions for a local calendar date and require 72 elapsed hours of notice.

## Development

```sh
mise install
pnpm install --frozen-lockfile
mise run check
pnpm build:apk
adb install -r artifacts/aall.apk
```

Android SDK platform 37.0, build tools 37.0.0 and NDK 27.1.12297006 are required. Install them with `sdkmanager 'platforms;android-37.0' 'build-tools;37.0.0' 'ndk;27.1.12297006'`. Expo uses Metro for Android. Vite+ runs formatting, lint and shared TypeScript tests. ktlint formats and lints the native Kotlin module. `pnpm check` runs both linters, and `pnpm format` applies both formatters. Native JVM tests run during APK builds. `pnpm start` and `pnpm android` provide a development client. Expo Go cannot load this module.

The `pnpm build:apk` output uses the generated Expo development signing certificate. Releases are built and signed locally with a persistent private key. Do not use development APKs as an Obtainium update source. Different signing certificates require uninstalling before switching and uninstalling deletes local settings.

## Phone setup

Grant accessibility, DND, exact alarm and notification access from Phone setup. A sideloaded app may need **Allow restricted settings** in Android app info before enabling accessibility.

Grayscale requires this one-time grant:

```sh
adb shell pm grant dev.danields.aall android.permission.WRITE_SECURE_SETTINGS
```

The setup screen has a ten-second grayscale test and manual restoration. Permission and secure-setting readback alone do not prove that GrapheneOS displays grayscale. Verify on the physical phone. Android Settings, phone calls, the launcher and aall remain accessible. This provides personal friction, not tamper-proof enforcement.

Schedules use local wall time and the starting weekday. Focus and bypass use elapsed time during a boot and recover their remaining wall-clock deadline on reboot. Native alarms, boot/time broadcasts and accessibility events reconcile state without React running. The app reconciles after each action and when it returns to the foreground, and its one-second snapshot poll only reads. Android force-stop suspends an app until the next launch. Day-off midnight boundaries release aall's restrictions without releasing another app's DND rule.

The status notification comes from the native reconcile, so it also updates when React isn't running. Its channel bypasses DND, which Android only allows once aall has DND access. Without the bypass, aall's own DND rule hides it.

## Releases

The public repository is https://github.com/DanielSintimbrean/aall. Builds and releases run locally. GitHub Actions is not used. Add this repository URL to Obtainium using its GitHub source.

Run `pnpm release` after committing the new version. It uses the local signing files in `~/.local/share/aall/signing/`. Override `AALL_KEYSTORE` and `AALL_PASSWORD_FILE` to use different private paths. The script runs checks, builds the standalone APK, signs it, verifies its signature and alignment, and publishes it to GitHub Releases. Bump `package.json` and `app.config.ts` versions before each new release. Version codes default to Unix seconds, increasing across releases. Do not release twice with the same tag.

The default key path is `~/.local/share/aall/signing/aall.jks`. Keep a private backup of it and its password. Never recreate the key, because Android rejects updates signed with a different one.

## Validation

See [VALIDATION.md](VALIDATION.md) for the latest measured checks and outstanding device tests.
