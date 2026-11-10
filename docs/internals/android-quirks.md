# Android quirks

What Android does on the Pixel 8 Pro (GrapheneOS, Android 17) that its documentation doesn't say, and that aall's code or tooling depends on.

## Display

- Writing `night_display_color_temperature` updates the setting and `dumpsys color_display`, but the color matrix keeps its old value until color correction switches. Turning Night Light off and on doesn't help. So every temperature write also toggles `accessibility_display_daltonizer_enabled` (`Engine.refreshNightLightMatrix`).
- `display_color_mode` would also recompute the matrix, but only `adb shell` may write it. The app gets "You cannot change private secure settings".
- Screenshots show neither grayscale nor Night Light, and reading the settings back doesn't prove the screen changed. Only `colorTransformMatrix` in `dumpsys SurfaceFlinger` shows what the display applies. With both on, the matrix combines them.
- The temperature limits come from `config_nightDisplayColorTemperatureMin` and `Max`. On this phone they are 686 K and 4082 K, not AOSP's usual 2596 K minimum, so 50% intensity is 2384 K.

## Charging

- Power broadcasts don't reach manifest receivers. aall registers its power receiver at runtime in `AallAccessibilityService`, so a charging change reaches reconcile at once only while accessibility is on. Otherwise the next reconcile sees it.
- `ACTION_POWER_CONNECTED` arrives about 10 ms before `BATTERY_CHANGED`, so the sticky battery status often still reads unplugged. The receiver passes the plug state from the action into reconcile.

## Accessibility and windows

- Opening the notification shade sends a window state event for System UI. Closing it sends none for the app below, so aall also reads the active window on `TYPE_WINDOWS_CHANGED`. Listing windows needs `canRetrieveWindowContent`.
- aall's own blocking overlay can hold focus while a permitted app opens with no window state event. When the overlay is the active window, aall reads the application window below it.
- Keyboard popups, such as accents and emoji, are separate windows from the keyboard's `SoftInputWindow`. aall ignores every window from an enabled keyboard.
- `resolveActivity` for `ACTION_MAIN` + `CATEGORY_HOME` returns `com.android.settings` instead of the real launcher unless the manifest declares the HOME intent under `queries`.
- The phone's launcher, Kvaesitso, runs its own accessibility service, so `enabled_accessibility_services` holds both.
- A sideloaded app may need **Allow restricted settings** in Android's app info before accessibility can be turned on.

## Test tools

- `uiautomator dump` suspends every accessibility service while it runs, so aall briefly reports accessibility missing and Today's layout shifts.
- agent-device's snapshot helper (`com.callstack.agentdevice.snapshothelper`) keeps a UiAutomation running after a snapshot, which suspends every accessibility service until the helper stops.
- Force-stopping aall, including `agent-device open --relaunch`, removes its service from `enabled_accessibility_services`. Force-stop also cancels aall's alarms until the next launch.

## Notifications

- A channel's DND bypass only takes effect once the app has DND access. `StatusNotifier` recreates the channel on every post so it gains the bypass after access is granted. Without it, aall's own DND rule hides the status notification.

## Build output

- Expo's Gradle plugins warn about the obsolete variant API under AGP 9.2.1. The build succeeds, but these plugins need updates before AGP 10.
- pnpm warns that Vitest's Vite range doesn't match the Vite+ 1.5.0 alias. Vite+ uses its bundled Vite and the tests run.

## Not yet verified on the phone

The JVM tests cover the rule boundaries. These need real elapsed time, a change in the phone's state, or hardware the emulator lacks:

- A real reboot, timezone change and clock change, including a turn-off notice and Night Light window across a reboot.
- Bedtime overnight through wake-up, started by a real cable change, with DND access granted.
- A day-off midnight transition, and a full 15-minute pause while the phone is idle.
- The alarm that ends a turn-off notice at its deadline, and an alarm-driven Night Light window end.
- A Night Light ramp overnight under real Doze.
- Color exceptions while Camera, Photos and Maps are in front.
- Status notification on the lock screen; the App access, Bedtime, Grayscale, day-off and needs-setup notices; and granting DND access after the channel exists.
- Keyboard popups with the phone's own keyboard, and the lock screen with a PIN.
- A picture-in-picture window during a block.
- An Obtainium update.
