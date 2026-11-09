---
name: android-emulator
description: Per-branch Android emulator for aall. Use before installing, launching or screenshotting aall on an emulator, and when you are done with the device.
---

# Android emulator

Every branch gets its own emulator, `aall-<branch>`, created by [`scripts/emulator.sh`](../../../scripts/emulator.sh). Parallel sessions own their device, so nobody waits for or overwrites another session's install. Leave `aall-test`, other branches' emulators and the user's phone (`3B291FDJG004K7`) to their owners.

## Start

1. `scripts/emulator.sh create` makes the AVD for the current branch. It is idempotent; `scripts/emulator.sh name` prints the name.
2. T3 `device_list`, then `device_open` with `hostId: local` and `deviceId` set to that name. It boots the AVD into the user's Device panel and returns the `agent-device` command for every UI action.
3. `serial=$(scripts/emulator.sh serial)` waits until Android has booted and prints the serial. Pass `-s "$serial"` to every `adb` call, because other emulators and the phone are attached too.
4. Build for the emulator's ABI and install:
   ```sh
   AALL_ABIS=x86_64 AALL_VERSION_CODE=$(date +%s) pnpm build:apk
   adb -s "$serial" install -r artifacts/aall.apk
   ```
   The default build is arm64 only and crashes at launch on the emulator with a SoLoader error.
5. `scripts/emulator.sh setup` grants what Phone setup asks for: accessibility, DND, exact alarms, notifications and `WRITE_SECURE_SETTINGS`. Skip it when Phone setup itself is under test.

The AVD starts fresh: Android defaults, all routines off. Reuse it for every check on the branch.

## Finish

When the task no longer needs the device, before handing back:

1. `device_close` with `shutdown: true`.
2. `scripts/emulator.sh delete`.

Each running emulator holds about 2 GB of RAM. `scripts/emulator.sh list` shows every branch AVD with its serial and worktree. `scripts/emulator.sh prune` deletes the stopped ones whose worktree is gone and names any that are still running. `delete` and `prune` only touch AVDs this script created.
