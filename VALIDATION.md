# Validation

2026-11-04, Pixel 8 Pro running GrapheneOS Android 17, SDK 37.

## Passed

- Expo dependency compatibility check.
- TypeScript strict typecheck, Vite+ formatting and lint, ktlint formatting and lint for Kotlin.
- 27 shared TypeScript tests for defaults, input validation, screen labels and routine drafts.
- 83 native JVM tests for Night Light ramps and restoration, Movent boards, the turn-off notice, overnight ownership, Only today, elapsed timers and reboot deadlines, overlapping requests, bypass suppression and expiry, retry verification, DST booking boundaries, the earliest bookable day off, timezone-aware temporary expiry, 10,000 generated arithmetic challenges, config parsing, charging-started bedtime, day-off transitions, DND ownership, blocking rules, routine status, next-change scheduling and status notices.
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

## Turn-off notice checks

Emulator `aall-test`, Android 17, on Grayscale during its active hours. A pass here doesn't clear the clock, reboot or elapsed-time checks for the physical phone.

- Turning the switch off opened the confirm dialog. Keep on left it on. Starting the notice showed the deadline in the editor and on Today, disabled the switch, and left grayscale on.
- Moving the clock forward 4 days kept the routine on during the same boot. The shown deadline stayed 72 hours from the request in elapsed time.
- Keep on ended the notice. A real reboot kept the notice and its deadline.
- With `boot_count` raised and the clock 4 days ahead, standing in for a reboot past the deadline, the routine showed Disabled and grayscale turned off. No real 72 hours passed, so the alarm firing at the deadline is covered only by the JVM test on `nextChange`.
- Turning it back on with Save weekly applied at once.

## Night Light checks

2026-11-05, physical Pixel 8 Pro, GrapheneOS Android 17 (`CP3A.261005.005`), release-signed build installed over the existing app. Settings were preserved, and the new routine appeared disabled.

Limits on this phone: `config_nightDisplayColorTemperatureMin` 686 K, `Max` 4082 K, `Default` 4000 K. AOSP's usual 2596 K minimum doesn't apply here, and 50% is 2384 K.

Reading settings doesn't prove the filter changed, so these checks read `colorTransformMatrix` from `dumpsys SurfaceFlinger`:

- Writing `night_display_color_temperature` updates the setting and `dumpsys color_display`, but the matrix doesn't move, even when turning the filter off and on. This contradicts what AOSP's observer suggests. At 2000 K and 3500 K, green and blue stayed at 0.695 and 0.403.
- Writing `display_color_mode` recomputes the matrix from `adb shell`, but the app gets "You cannot change private secure settings". Toggling `accessibility_display_daltonizer_enabled` works from the app.
- A 15-minute ramp from aall to 50% moved the matrix every minute with the app in the background. Green and blue went from 0.738 and 0.483 at 4082 K to 0.544 and 0.232 at 2837 K, on the steps logged. The user watched this one and saw a slight flash at each step without grayscale, which they accepted, and none with grayscale.
- Screenshots don't show the filter or grayscale. Only the matrix and the user's eyes do.
- With grayscale on, the matrix combines both, for example 0.213, 0.715, 0.072 scaled to 0.527 and 0.347 for green and blue. Solving the arithmetic pause released grayscale and left the ramp running.
- Reinstalling or `am kill` during a window resumed at the temperature for that moment, without React in the foreground.
- During a window the schedule read `night_display_auto_mode` 0, and Android's Night Light settings showed no schedule.
- Writing a different temperature by hand during a window stopped the ramp from overwriting it. Ending the window then restored the activated state and schedule and left the hand-set temperature.
- Ending a window early from the editor restored activated 0, 2327 K and schedule 1, exactly as they were, and the phone's own 19:50 schedule was still set.
- The user tried different settings in the editor and in Android's Night Light screen and reported that it works.

Two test tools change what aall reports. The `agent-device` snapshot helper keeps a UiAutomation running, which suspends every accessibility service until it's stopped. Force-stopping aall, including `open --relaunch`, removes its service from `enabled_accessibility_services`.

Not tested on the phone: real Doze, a real reboot, an overnight ramp, and an alarm-driven window end. Elapsed-time logic for these is covered by JVM tests on the ramp, and the reboot case relies on the stored session and wall-clock timing.

## Charging events

Physical phone, with simulated events. `dumpsys battery unplug` and `reset` sent the real `ACTION_POWER_*` broadcasts, confirmed by logging in `AutomationReceiver`. No cable was moved.

- With accessibility off, eight power broadcasts reached no manifest receiver while the app process stayed alive. `MY_PACKAGE_REPLACED` still reached it, so the power actions were removed from the manifest.
- Android 17 sends `ACTION_POWER_CONNECTED` about 10 ms before `BATTERY_CHANGED`. In five of six connects the sticky battery status still read unplugged, so the receiver passes the plug state from the action into reconcile.
- With Bedtime enabled in its early-start window, a connect turned on DND and grayscale within a second and Today showed Bedtime running. Unplugging kept both on. Accessibility must be on for a charging change to reach reconcile immediately. Otherwise the next reconcile sees it.

## Notification shade and keyboard checks

2026-11-06, emulator `aall-test`, Android 17, Gboard as the keyboard. Bedtime was set to start two minutes ahead, with Maps and the clock allowed.

Before the fix:

- With Chrome in front and no interaction, the alarm at the start time showed the blocking screen.
- After opening and closing the notification shade in Chrome, the start time passed with no blocking screen, and Chrome stayed usable. Android sends a window state event when the shade opens, but none for the app below when it closes, so aall still treated System UI as the app in front.
- In Maps, a long press on a Gboard key opened the accent popup and showed the blocking screen. Gboard's popups are separate windows that aren't the keyboard's `SoftInputWindow`.

After the fix, which ignores every window from an enabled keyboard and reads the active window when windows change:

- After opening and closing the shade in Chrome, the blocking screen appeared at the start time.
- On a blocked app, opening the shade removed the blocking screen and closing it brought it back.
- In Maps, the accent popup and the emoji panel left the keyboard usable, with no blocking screen.

The service now needs `canRetrieveWindowContent` to list windows. It reads only the active window's package. Not tested on the physical phone: its keyboard and the lock screen with a PIN.

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

## Home visibility during bedtime

2026-11-09, Android 17 emulator. A temporary debug Activity called the same
`resolveActivity(ACTION_MAIN + CATEGORY_HOME, 0)` as `Engine.environment`, from
aall's own UID. With the original manifest it returned `com.android.settings`,
although the selected home was Pixel Launcher. With the HOME query declared it
returned `com.google.android.apps.nexuslauncher`. The probe was removed from the
build afterward. This confirms why the existing always-allowed launcher exception
was protecting the wrong package. The existing reconcile test also checks that an
always-allowed launcher remains usable during bedtime while a game is blocked.

Typecheck, Vite+ checks, ktlint, 27 shared tests and the native JVM suite passed.

The final release APK built successfully, passed signature and zip alignment
verification, and was installed over the existing app on the physical Pixel 8 Pro.
The phone uses Kvaesitso. Existing schedules were preserved; no bedtime override
was applied on the phone. After closing the device session, dumpsys confirmed both
aall and Kvaesitso accessibility services were bound. A full bedtime interaction
on the physical phone has not been verified.

## Essential utilities during Descanso

2026-11-10, branch-owned Android 17 emulator and physical Pixel 8 Pro.
Added explicit exceptions for document pickers, download management, Google Files,
Amaze, Settings Intelligence, and package/certificate installation. Window changes
now inspect the application below the accessibility overlay: the overlay can hold
focus when a permitted application opens and no application state event arrives.

Reproduced the stuck overlay on the emulator before the window handling change.
With the final APK, activated Descanso using Only today on both devices. Chrome
was blocked; switching to Settings removed the overlay on both. Downloads opened
on both and Google Files opened on the phone. Screenshots were captured without
an active UIAutomator reader, which otherwise temporarily suppresses aall's
accessibility service. Confirmed the service was bound during phone checks.

Installed the final APK on the phone with the existing release signing key and
without uninstalling. Restored the phone's original disabled weekly Descanso
configuration and removed the test override. Enabled aall accessibility during
setup, preserving the launcher's service. Phone DND and exact-alarm access remain
ungranted; their effects were not validated on the phone. Deleted the branch AVD.

94 native JVM tests, 27 shared tests, typecheck, project lint and Kotlin formatting
passed. The native suite includes essential-utility exemptions during Descanso
and verifies an ordinary unlisted app remains blocked.

Full-send review: Sol identified that a higher-layer picture-in-picture window
could displace the active application. Prefer the active application, and only
fall back to the underlying application when an accessibility overlay is active.
Sol approved this adjustment in round 2. Rebuilt the APK and repeated the active
Descanso Chrome-to-Settings transition on a fresh branch emulator with the service
bound; Chrome was blocked and Settings opened without the overlay. PiP selection
was reviewed statically, not exercised with a video. Installed this final signed
APK on the Pixel without changing its restored routine configuration, and deleted
the branch emulator again.
