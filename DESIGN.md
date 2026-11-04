# aall design

## Purpose and scope

Personal Android automation app with fixed automation types in code. Daily use means adjusting hours, weekdays, and app lists directly on the phone. No generic rule builder, accounts, server, or external configuration files.

The first release contains scheduled app blocking, manually started focus sessions, bedtime, and scheduled grayscale. Other my-tasker features are deferred.

It also includes scheduled days off without restrictions. A day off must be booked at least 72 hours before the day begins.

## Agreed behavior

- Each routine has its own blocked-app list; bedtime instead has an allowed-app list.
- Phone calls, Android Settings, and aall stay accessible.
- Weekly schedules are normal. "Only today" makes a temporary adjustment.
- A day off provides a calendar day without aall restrictions, including focus and bedtime, and requires a minimum of 72 hours of advance notice before its starting midnight. Cancellation is allowed at any time. Moving a booking requires meeting the 72-hour deadline for the new date.
- Overlapping routines retain their restrictions independently. Ending focus does not release bedtime restrictions.
- A bypass presents an arithmetic expression with three integers in the inclusive range 50 to 999 and addition and subtraction. The final result must be in the inclusive range -999 to 999.
- A correct answer pauses restrictions for 15 minutes. Current routines resume afterward. Wrong answers retry the same expression.
- Scheduled bedtime starts without requiring charging. Charging during an earlier evening window can start bedtime early. Unplugging does not cancel it. The configured wake-up time ends bedtime.
- A single silent status notification shows what Today's heading shows: running routines, pause or day off, with a countdown for focus and pause. It goes away when nothing is in effect, and a swiped-away notice stays gone until the status changes. It must stay visible during aall's own DND.
- Today is the home screen: active routines and their reasons, next change, Start Focus, and shortcuts for times, days, and app lists.
- One-time ADB setup for grayscale is acceptable, subject to proving it works on the actual phone.
- Public GitHub repository, locally built and signed APKs, GitHub Releases, and Obtainium updates. Personal adjustments stay on-device.

## Initial defaults

Seed the scoped features from my-tasker without requiring a runtime connection to Tasker or importing external configuration files:

- Focus duration: 50 minutes. Focus requests DND, grayscale, and blocking of its selected apps.
- Bedtime: 22:30 to 07:00 every day. Earliest charging-triggered bedtime: 20:00. Bedtime requests DND, grayscale, and blocks apps outside its allowed list.
- Scheduled app access: 12:30 to 13:15 and 19:00 to 20:00 every day. Selected apps are blocked outside those windows; focus or bedtime can still block them inside a window.
- Grayscale: weekdays 09:00 to 13:00 and 14:00 to 18:00; daily 21:00 to 07:00. Camera, Photos, and Maps retain color while foregrounded.
- Reuse the relevant my-tasker app selections as initial suggestions, matched against installed packages. App lists remain editable on the phone.
- Initial routines are disabled until the user enables them and grants their required access.

The schedule editor explicitly labels app windows as "Allowed hours" and grayscale windows as "Active hours".

## Time and recovery rules

- Overnight schedules belong to their starting weekday. Monday 22:30 to 07:00 ends Tuesday morning.
- An overnight "Only today" adjustment lasts until that occurrence ends, including after midnight, then the weekly schedule resumes.
- Use phone-local wall-clock time for weekly schedules and elapsed duration for focus and bypass countdowns during a running boot.
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
- Persist adjustments locally. Use Android scheduling and persisted native-readable routine state so execution does not depend on the React UI staying open.
- No Tasker, legacy helper, or Shizuku dependency is assumed.

## Day-off behavior

- A day off is one calendar date, from local midnight until the following local midnight. Require at least 72 elapsed hours between booking and the start of that date, including across daylight-saving changes.
- Suppress all aall restrictions for the date, including bedtime carried over from the previous evening, app blocking, DND, grayscale, and any previously running focus restriction. Do not alter DND or other settings independently owned by the user or another app.
- At the end of the day off, evaluate current routines and resume restrictions that apply at that time. Do not shift schedules or extend focus sessions.
- Do not allow an "Only today" control or routine pause to create or move a scheduled day off inside the 72-hour notice period. The separately agreed arithmetic bypass still grants its normal 15-minute pause.
- Allow cancelling a day off at any time. Moving it is a cancellation plus a new booking, subject to a fresh 72-hour check.
- Persist bookings across app restarts and reboots. Day-off dates and scheduling use the phone's local timezone; handle time changes by reconciling state.
- The day-off UI shows eligible dates and the earliest eligible date. Bookings select individual dates.
