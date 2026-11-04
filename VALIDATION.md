# Validation

2026-11-04, Pixel 8 Pro running GrapheneOS Android 17, SDK 37.

## Passed

- Expo dependency compatibility check.
- TypeScript strict typecheck, Vite+ formatting and lint, ktlint formatting and lint for Kotlin.
- Four shared TypeScript tests for defaults, input validation and 72-hour booking boundaries.
- 41 native JVM tests for overnight ownership, Only today, elapsed timers and reboot deadlines, overlapping requests, bypass suppression and expiry, retry verification, DST booking boundaries, timezone-aware temporary expiry, 10,000 generated arithmetic challenges, config parsing, charging-started bedtime, day-off transitions, DND ownership, blocking rules, routine status, next-change scheduling and status notices.
- Metro production Android export.
- Independent arm64 standalone release APK build, including native compilation and Android release lint.
- APK signed with persistent release certificate, installed and launched through ADB without Metro.
- Version 0.1.1 built, signed and published locally. The downloaded GitHub Release asset matched the local SHA-256 and installed successfully over the earlier build, preserving app data.
- Native WRITE_SECURE_SETTINGS permission granted on the physical phone.
- Native grayscale test changed daltonizer enabled/mode to 1/0, then restored the original 0/0. The user visually confirmed gray buttons followed by restored color.

## Device checks

- Focus activated Android's implicit `dev.danields.aall` DND rule.
- Accessibility service bound after allowing Android restricted settings, alongside the existing launcher service.
- Adding Chrome to Focus's blocked list displayed the native full-screen blocking overlay when Chrome launched. The overlay offered Home and a route back to aall.
- Wrong arithmetic answers kept the same challenge. The correct answer displayed a 15-minute pause, deactivated aall's implicit DND rule and restored grayscale enabled/mode to 0/0.
- Other Android DND rules remained present. An independently active competing DND rule was not toggled for testing.

## Interface checks

Physical phone, on the build rebased onto `Reconcile`:

- Android back moved through editor, app picker, days off, setup and pause, and returned to Today without closing the app.
- Leaving an edited routine asked to discard. Keep editing preserved the change. A weekly save persisted after reopening and was restored afterward.
- Native time and date dialogs opened, and the date dialog started at the earliest bookable date. A day off was booked and cancelled.
- A wrong answer showed an inline error and kept the same sum. The right answer showed Paused on Today. Check answer stayed above the keyboard.
- Starting Focus showed the running heading with no Start button and turned on DND and grayscale. Solving the sum showed Paused with Start still hidden and released both. Disabling Focus showed Set up focus.
- The seven weekday buttons fit at a display width of 320dp.

## Status notification checks

Physical phone, release-signed build installed over 0.1.1 with settings preserved:

- The Notifications row in Phone setup opened Android's app notification settings. Turning them on granted `POST_NOTIFICATIONS`, and the row and Today updated on return.
- With every routine off, aall posted no notification.
- During a one-minute Focus, with `zen_mode` 1 from aall's own rule, the shade showed Focus with a running countdown. Android logged `not_intercepted … priorityApp`, with `mIntercept=false` and `mBypassDnd=true`.
- When the timer ended, the notification went away and DND and grayscale were released.
- Swiping it away during Focus held through going home and reopening aall. Android logged one post per session, so app switches did not repost it.
- Solving the sum showed Paused with a 15-minute countdown. Installing an update during the pause kept the notification and its deadline. When the pause ended, the notification went away.

The checks above ran on a build that also set a notification timeout and public lock-screen visibility. Both were removed afterward, so this was repeated on the final build:

- During a one-minute Focus under the phone's own scheduled DND rule, the shade showed the countdown with `mIntercept=false`.
- With the launcher in the foreground and no interaction, the alarm alone cleared the notification and released grayscale. The other DND rule stayed on.

Not tested on the phone: the lock screen, App access, Bedtime and Grayscale notices, day off, needs setup and granting DND access after the channel exists. JVM tests cover the wording and priority of those notices.

`uiautomator dump` suspends accessibility services while it runs, so aall briefly reports accessibility as missing during UI automation. Confirm that state from a screenshot taken without a dump.

## Charging events

Physical phone, with simulated events. `dumpsys battery unplug` and `reset` sent the real `ACTION_POWER_*` broadcasts, confirmed by logging in `AutomationReceiver`. No cable was moved.

- With accessibility off, eight power broadcasts reached no manifest receiver while the app process stayed alive. `MY_PACKAGE_REPLACED` still reached it, so the power actions were removed from the manifest.
- Android 17 sends `ACTION_POWER_CONNECTED` about 10 ms before `BATTERY_CHANGED`. In five of six connects the sticky battery status still read unplugged, so the receiver passes the plug state from the action into reconcile.
- With Bedtime enabled in its early-start window, a connect turned on DND and grayscale within a second and Today showed Bedtime running. Unplugging kept both on. Accessibility must be on for a charging change to reach reconcile immediately. Otherwise the next reconcile sees it.

## Remaining verification

- The device checks above ran before the logic moved into `Reconcile` and the snapshot became read-only. Repeat them on the current build.
- Reboot, timezone and clock changes on the physical phone.
- Overnight and charging-triggered bedtime through wake-up time on the physical phone, including a real cable change.
- Day-off midnight transitions and a full 15-minute bypass while the device is idle.
- Color exceptions while Camera, Photos and Maps are foregrounded.
- Obtainium update installation.

These checks require real elapsed time or changes to the phone's operating state. JVM tests cover the rule boundaries but do not prove Android delivery under every battery or idle condition.

## Tooling warnings

Vite+ 1.5.0 uses its bundled Vite implementation. pnpm reports Vitest's upstream Vite version range against the Vite+ alias version. The shared tests run successfully. Expo's native Gradle plugins emit obsolete variant API warnings under AGP 9.2.1; native build succeeds. These dependencies will need updates before AGP 10.
