# aall design

## Purpose and scope

Personal Android automation app with fixed automation types in code. Daily use means adjusting hours, weekdays, and app lists directly on the phone. No generic rule builder, accounts, server, or external configuration files.

The first release contains scheduled app blocking, manually started focus sessions, bedtime, scheduled grayscale, and a Night Light ramp. Other my-tasker features are deferred.

It also includes scheduled days off without restrictions. A day off must be booked at least 72 hours before the day begins.

## Apps

Apps are small tools for one personal need, written directly in this codebase. Each one lives in `src/apps/<name>` and is added to `src/apps/index.ts`, which puts it on Today and in navigation. An app that needs Android code has its own package and Expo module beside `automation`. Apps do not depend on routines.

The first app, Movent, shows the next 2 hours of buses at Cascada de les Moles (stop 126). Turned on, a foreground service keeps a silent notification with the next bus and those after it, refreshing every minute while the screen is on and resuming after a reboot. Each time is marked GPS when Mou-te gives an updated time, Localizado when the bus has a position but no updated time, and Sin confirmar when it is under 30 minutes away with neither. The data comes from the undocumented gateway behind Mou-te's web app, which may change without notice.

## Agreed behavior

- Each routine has its own blocked-app list; bedtime instead has an allowed-app list.
- Phone calls, Android Settings, the launcher, and aall stay accessible.
- Weekly schedules are normal. "Only today" makes a temporary adjustment. Saving weekly settings ends that routine's "Only today" change.
- A day off provides a calendar day without aall restrictions, including focus and bedtime, and requires a minimum of 72 hours of advance notice before its starting midnight. Cancellation is allowed at any time. Moving a booking requires meeting the 72-hour deadline for the new date.
- Turning a routine on takes effect at once. Turning off a routine that's on weekly requires 72 elapsed hours of notice, and it keeps following its schedule until then. "Keep on" cancels the notice at any time. "Only today" can't turn a routine off. Editing hours and app lists stays immediate, so it can still weaken a routine.
- Overlapping routines retain their restrictions independently. Ending focus does not release bedtime restrictions.
- A bypass presents an arithmetic expression with three integers in the inclusive range 50 to 999 and addition and subtraction. The final result must be in the inclusive range -999 to 999.
- A correct answer pauses restrictions for 15 minutes. Current routines resume afterward. Wrong answers retry the same expression.
- Scheduled bedtime starts without requiring charging. Charging during an earlier evening window can start bedtime early. Unplugging does not cancel it. The configured wake-up time ends bedtime.
- A single silent status notification shows what Today's heading shows: running routines, pause or day off, with a countdown for focus and pause. It goes away when nothing is in effect, and a swiped-away notice stays gone until the status changes. It must stay visible during aall's own DND.
- Today is the home screen: active routines and their reasons, missing permissions, "Only today" hours in effect, next change, Start Focus, and shortcuts for times, days, and app lists.
- One-time ADB setup for grayscale is acceptable, subject to proving it works on the actual phone. Phone setup has a ten-second grayscale test and a manual restore.
- Public GitHub repository, locally built and signed APKs, GitHub Releases, and Obtainium updates. Personal adjustments stay on-device.

## Initial defaults

Seed the scoped features from my-tasker without requiring a runtime connection to Tasker or importing external configuration files:

- Focus duration: 50 minutes. Focus requests DND, grayscale, and blocking of its selected apps.
- Bedtime: 22:30 to 07:00 every day. Earliest charging-triggered bedtime: 20:00. Bedtime requests DND, grayscale, and blocks apps outside its allowed list.
- Scheduled app access: 12:30 to 13:15 and 19:00 to 20:00 every day. Selected apps are blocked outside those windows; focus or bedtime can still block them inside a window.
- Grayscale: weekdays 09:00 to 13:00 and 14:00 to 18:00; daily 21:00 to 07:00. Camera, Photos, and Maps retain color while foregrounded.
- Night Light: 21:00 to 07:00 every day, a 90-minute ramp up to 50% intensity. The ramp finishes when the default bedtime starts, and 50% is about 2384 K on the Pixel 8 Pro, close to the 2327 K the phone was already set to.
- Reuse the relevant my-tasker app selections as initial suggestions, matched against installed packages. App lists remain editable on the phone.
- Initial routines are disabled until the user enables them and grants their required access.

The schedule editor explicitly labels app windows as "Allowed hours" and grayscale windows as "Active hours".

## Time and recovery rules

- Overnight schedules belong to their starting weekday. Monday 22:30 to 07:00 ends Tuesday morning.
- An overnight "Only today" adjustment lasts until that occurrence ends, including after midnight, then the weekly schedule resumes.
- Use phone-local wall-clock time for weekly schedules and elapsed duration for focus and bypass countdowns during a running boot. After a reboot, focus and bypass keep their remaining wall-clock deadline.
- Reconcile active routines after reboot, time changes, and timezone changes. Reapply current restrictions after services restart; do not replay actions for intervals that already ended.
- A bypass does not erase or extend a routine. When it expires, evaluate whatever routines are active then.
- DND and grayscale requests must be coordinated so one ending routine does not clear another's request. Release only settings owned by aall and avoid overriding user changes.
- Missing permission is shown on the affected routine with a setup action; do not claim the routine is protecting the phone when it cannot run.
- The app provides personal friction, with Android Settings remaining available. It does not promise tamper-proof enforcement.

## Implementation direction

- React Native, Expo development builds, and Uniwind for the interface.
- Local Kotlin Expo module for accessibility-based app blocking, native scheduling, charging events, DND, and grayscale integration.
- Expo Metro for Android bundling. Vite+ runs repository tooling and pure TypeScript tests.
- mise pins development tools. Pin package dependencies after checking current compatibility.
- Persist adjustments locally. Use Android scheduling and persisted native-readable routine state so execution does not depend on the React UI staying open. Native alarms, boot and time broadcasts, and accessibility events reconcile without React, and so does the status notification.
- The app reconciles after each action and when it returns to the foreground. Its one-second snapshot poll only reads.
- No Tasker, legacy helper, or Shizuku dependency is assumed.

## Night Light behavior

- The routine controls Android's own Night Light through `night_display_activated`, `night_display_color_temperature` and `night_display_auto_mode`, using the same one-time ADB grant as grayscale.
- It has one window. The ramp starts when the window starts, and the routine releases the filter when the window ends. Intensity uses the scale of Android's slider: 0% is the device's highest temperature limit and 100% its lowest. Limits come from the device's `config_nightDisplayColorTemperatureMin` and `Max`.
- It isn't a restriction. A pause or a day off doesn't release it, and turning it off needs no 72-hour notice. "Only today" can turn it off.
- Writing the temperature doesn't change the filter on Android 17, even though the setting and `dumpsys color_display` report the new value. Android recomputes its matrix when color correction is switched, so each temperature write also toggles `accessibility_display_daltonizer_enabled`. `display_color_mode` does the same but is private to apps. Toggling switches the display's color mode for a moment, and with grayscale on it leaves grayscale for a moment.
- The temperature follows the wall clock from the window's start, written every minute. Late steps only make the next change bigger, so a restart, Doze or a closed app can't shift the ramp. Starting inside a window goes straight to the temperature for that moment.
- Steps use a non-wakeup alarm, so the ramp doesn't wake a sleeping phone. The first step after waking catches up. The window's start and end still use the normal waking alarm.
- Android's weakest Night Light is already warm, so turning on is a small visible change. Android fades that change in.
- While running, the routine turns off Android's own Night Light schedule, which could otherwise switch the filter off in the middle of a window.
- A filter that's already on when the window starts sets the ramp's starting point, and the ramp never makes it weaker.
- Changing the filter, its temperature or the schedule during a window hands control back until the window ends.
- At the end, aall restores each setting that still holds what aall wrote. It restores the schedule last, so Android decides whether the filter belongs on at that time. The earlier values are stored in native preferences, so this survives a restart.
- Force-stopping aall stops its alarms until the next launch, like every other routine. Android's schedule stays off until then.

## Day-off behavior

- A day off is one calendar date, from local midnight until the following local midnight. Require at least 72 elapsed hours between booking and the start of that date, including across daylight-saving changes.
- Suppress all aall restrictions for the date, including bedtime carried over from the previous evening, app blocking, DND, grayscale, and any previously running focus restriction. Do not alter DND or other settings independently owned by the user or another app.
- At the end of the day off, evaluate current routines and resume restrictions that apply at that time. Do not shift schedules or extend focus sessions.
- Do not allow an "Only today" control or routine pause to create or move a scheduled day off inside the 72-hour notice period. The separately agreed arithmetic bypass still grants its normal 15-minute pause.
- Allow cancelling a day off at any time. Moving it is a cancellation plus a new booking, subject to a fresh 72-hour check.
- Persist bookings across app restarts and reboots. Day-off dates and scheduling use the phone's local timezone; handle time changes by reconciling state.
- The day-off UI shows eligible dates and the earliest eligible date. Bookings select individual dates.
