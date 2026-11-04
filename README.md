# aall

Personal Android routines with on-phone editing. React Native, Expo and Uniwind provide the UI; a local Kotlin Expo module owns persistence, scheduling and enforcement. No account or backend. The approved behavior is in [DESIGN.md](DESIGN.md).

All routines start disabled. Today shows scheduled activity and missing permissions. Editors configure allowed hours for app access, active grayscale hours, bedtime, weekdays and per-routine app lists. Focus defaults to 50 minutes. An arithmetic challenge pauses every restriction for 15 minutes. Days off suppress all restrictions for a local calendar date and require 72 elapsed hours of notice.

## Development

```sh
mise install
pnpm install --frozen-lockfile
mise run check
pnpm build:apk
adb install -r artifacts/aall.apk
```

Android SDK platform 37.0, build tools 37.0.0 and NDK 27.1.12297006 are required. Install them with `sdkmanager 'platforms;android-37.0' 'build-tools;37.0.0' 'ndk;27.1.12297006'`. Expo uses Metro for Android. Vite+ runs formatting, lint and shared TypeScript tests. Native JVM tests run during APK builds. `pnpm start` and `pnpm android` provide a development client. Expo Go cannot load this module.

Local APKs use the generated Expo development signing certificate. Releases are re-signed with a persistent private key in GitHub Actions. Do not use development APKs as an Obtainium update source. Different signing certificates require uninstalling before switching and uninstalling deletes local settings.

## Phone setup

Grant accessibility, DND and exact alarm access from Phone setup. A sideloaded app may need **Allow restricted settings** in Android app info before enabling accessibility.

Grayscale requires this one-time grant:

```sh
adb shell pm grant dev.danields.aall android.permission.WRITE_SECURE_SETTINGS
```

The setup screen has a ten-second grayscale test and manual restoration. Permission and secure-setting readback alone do not prove that GrapheneOS displays grayscale. Verify on the physical phone. Android Settings, phone calls, the launcher and aall remain accessible. This provides personal friction, not tamper-proof enforcement.

Schedules use local wall time and the starting weekday. Focus and bypass use elapsed time during a boot and recover their remaining wall-clock deadline on reboot. Native alarms, boot/time broadcasts and accessibility events reconcile state without React running. Android force-stop suspends an app until the next launch. Day-off midnight boundaries release aall's restrictions without releasing another app's DND rule.

## Releases

The public repository is https://github.com/DanielSintimbrean/aall. Main and pull requests build an APK artifact and run checks. Tags matching `v*` publish a signed `aall-signed.apk` to GitHub Releases. Add this repository URL to Obtainium using its GitHub source.

GitHub Actions requires `AALL_KEYSTORE_BASE64`, containing the base64 encoded release keystore, and `AALL_STORE_PASSWORD`. The alias is `aall`; key and store passwords must match. Keep a private backup of the keystore and password outside the repository. Android version codes come from monotonically increasing workflow run numbers. Never recreate the signing key for an update.

## Validation

See [VALIDATION.md](VALIDATION.md) for the latest measured checks and outstanding device tests. The icon comes from `../danields.dev/public/icon-512.png`; initial app suggestions match `../my-tasker/config.json`. Neither reference project is required at runtime.
