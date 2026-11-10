---
name: android-emulator
description: Per-branch Android emulator for aall. Use before installing, launching or screenshotting aall on an emulator or the phone, and when you are done with the device.
---

# Android emulator

Every branch gets its own emulator, `aall-<branch>`, created by [`scripts/emulator.sh`](../../../scripts/emulator.sh). Parallel sessions own their device, so nobody waits for or overwrites another session's install. Leave `aall-test`, other branches' emulators and the user's phone (`3B291FDJG004K7`) to their owners.

## Start

1. `scripts/emulator.sh create` makes the AVD for the current branch. It is idempotent; `scripts/emulator.sh name` prints the name. It needs the x86_64 system image from the `sdkmanager` line in README.md's Development section.
2. T3 `device_list`, then `device_open` with `hostId: local` and `deviceId` set to that name. It boots the AVD into the user's Device panel and returns the `agent-device` command for every UI action. If T3 reports the command-line tools missing, the mise Android SDK lacks the `cmdline-tools/latest -> 23.0` symlink; add it under `~/.local/share/mise/installs/android-sdk/<version>/cmdline-tools/`.
3. `serial=$(scripts/emulator.sh serial)` waits until Android has booted and prints the serial. Pass `-s "$serial"` to every `adb` call, because other emulators and the phone are attached too.
4. Build for the emulator's ABI and install:
   ```sh
   AALL_ABIS=x86_64 AALL_VERSION_CODE=$(date +%s) pnpm build:apk
   adb -s "$serial" install -r artifacts/aall.apk
   ```
   The default build is arm64 only and crashes at launch on the emulator with a SoLoader error. Without `AALL_VERSION_CODE` the version code is 1 and Android refuses the install over a newer build.
5. `scripts/emulator.sh setup` grants what Phone setup asks for: accessibility, DND, exact alarms, notifications and `WRITE_SECURE_SETTINGS`. Skip it when Phone setup itself is under test.

The AVD starts fresh: Android defaults, all routines off. Reuse it for every check on the branch.

## Driving the UI

- Relaunch aall with `agent-device open` and no `--relaunch`. Force-stopping aall removes its accessibility service.
- After a snapshot, run `adb -s "$serial" shell am force-stop com.callstack.agentdevice.snapshothelper`. The helper keeps every accessibility service suspended until it stops, and stopping it restores them without touching any permission.
- `uiautomator dump` suspends accessibility while it runs, which adds warnings and shifts Today's layout. On Today, wait for two identical dumps before trusting coordinates. Confirm permission state from a plain screenshot.
- Prove grayscale and Night Light with `adb -s "$serial" shell settings get` and `dumpsys SurfaceFlinger`. Screenshots don't show either.

## The physical phone

Use it only when behavior must be proven on real hardware. The user may be holding it, and other sessions may install on it at the same time.

- Install only an APK signed with the release key, with `adb -s 3B291FDJG004K7 install -r`. A different key forces an uninstall, which deletes the user's settings.
- Check the focused window (`adb -s 3B291FDJG004K7 shell dumpsys window | grep mCurrentFocus`) right before every tap, and take a fresh screenshot after BACK. A pulled-down shade or a landscape Settings screen moves the target.
- Check `lastUpdateTime` in `dumpsys package dev.danields.aall` before trusting the build on screen.
- Enable a routine only during a pause or with the screen title verified, and restore every setting you changed before handing back.

## Finish

When the task no longer needs the device, before handing back:

1. `device_close` with `shutdown: true`.
2. `scripts/emulator.sh delete`.

Each running emulator holds about 2 GB of RAM. `scripts/emulator.sh list` shows every branch AVD with its serial and worktree. `scripts/emulator.sh prune` deletes the stopped ones whose worktree is gone and names any that are still running. `delete` and `prune` only touch AVDs this script created.
