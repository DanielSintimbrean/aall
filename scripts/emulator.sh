#!/usr/bin/env bash
# One Android emulator per branch, so parallel agent sessions never share a device.
# Usage: scripts/emulator.sh <create|name|serial|setup|delete|list|prune> [avd-name]
set -euo pipefail

image='system-images;android-37.0;google_apis;x86_64'
device_profile='pixel_8_pro'
package='dev.danields.aall'
a11y_service="$package/dev.danields.aall.automation.AallAccessibilityService"
owner_file='aall-owner'

die() {
  echo "emulator.sh: $*" >&2
  exit 1
}

branch_avd_name() {
  local branch
  branch=$(git rev-parse --abbrev-ref HEAD)
  [[ $branch != HEAD ]] || die 'detached HEAD; pass an AVD name'
  # The hash keeps branches that sanitize alike (fix/foo, fix-foo) on separate emulators.
  echo "aall-$(tr -c 'A-Za-z0-9._\n-' '-' <<<"$branch")-$(printf %s "$branch" | sha1sum | cut -c1-6)"
}

avd_dir() {
  local ini="${ANDROID_AVD_HOME:-$HOME/.android/avd}/$1.ini"
  [[ -f $ini ]] || return 1
  sed -n 's/^path=//p' "$ini"
}

owned() {
  local dir
  dir=$(avd_dir "$1") && [[ -f $dir/$owner_file ]]
}

serial_of() {
  local serial
  for serial in $(adb devices | awk '/^emulator-[0-9]+\tdevice/ {print $1}'); do
    if [[ $(adb -s "$serial" emu avd name 2>/dev/null | head -1 | tr -d '\r') == "$1" ]]; then
      echo "$serial"
      return 0
    fi
  done
  return 1
}

write_owner() {
  printf 'branch=%s\nworktree=%s\ncreated=%s\n' \
    "$(git rev-parse --abbrev-ref HEAD)" "$(git rev-parse --show-toplevel)" "$(date -Iseconds)" \
    >"$1/$owner_file"
}

cmd_create() {
  local name=$1 dir branch
  if dir=$(avd_dir "$name"); then
    owned "$name" || die "$name exists but was not made by this script"
    branch=$(sed -n 's/^branch=//p' "$dir/$owner_file")
    [[ $branch == "$(git rev-parse --abbrev-ref HEAD)" ]] || die "$name belongs to branch $branch"
    # The worktree may have moved; keep prune from treating the emulator as orphaned.
    write_owner "$dir"
    echo "$name already exists"
    return
  fi
  echo no | avdmanager create avd -n "$name" -k "$image" -d "$device_profile" >/dev/null
  dir=$(avd_dir "$name") || die "avdmanager did not create $name"
  # Same footprint as aall-test, so several can run at once.
  sed -i -e '/^hw.ramSize=/d' -e '/^hw.cpu.ncore=/d' -e '/^hw.keyboard=/d' "$dir/config.ini"
  printf 'hw.ramSize=2048M\nhw.cpu.ncore=4\nhw.keyboard=yes\n' >>"$dir/config.ini"
  write_owner "$dir"
  echo "$name created"
}

# Prints the serial once Android has finished booting, so installs never race the package service.
cmd_serial() {
  local serial
  serial=$(serial_of "$1") || die "$1 is not running; open it with T3 device_open first"
  adb -s "$serial" wait-for-device
  until [[ $(adb -s "$serial" shell getprop sys.boot_completed | tr -d '\r') == 1 ]] &&
    adb -s "$serial" shell pm path android >/dev/null 2>&1; do sleep 2; done
  echo "$serial"
}

cmd_setup() {
  local serial
  serial=$(cmd_serial "$1")
  adb -s "$serial" shell pm path "$package" >/dev/null || die "$package is not installed on $serial"
  adb -s "$serial" shell pm grant "$package" android.permission.WRITE_SECURE_SETTINGS
  adb -s "$serial" shell pm grant "$package" android.permission.POST_NOTIFICATIONS
  adb -s "$serial" shell cmd notification allow_dnd "$package"
  adb -s "$serial" shell appops set "$package" SCHEDULE_EXACT_ALARM allow
  # Right after an install Android can clear this setting again, so write until it reads back.
  for _ in $(seq 10); do
    adb -s "$serial" shell settings put secure enabled_accessibility_services "$a11y_service"
    adb -s "$serial" shell settings put secure accessibility_enabled 1
    sleep 2
    [[ $(adb -s "$serial" shell settings get secure enabled_accessibility_services | tr -d '\r') == "$a11y_service" ]] && break
  done
  [[ $(adb -s "$serial" shell settings get secure enabled_accessibility_services | tr -d '\r') == "$a11y_service" ]] ||
    die "accessibility did not stay enabled on $serial"
  echo "$1 ($serial): aall permissions granted"
}

cmd_delete() {
  local name=$1 serial
  owned "$name" || die "$name was not made by this script; refusing to delete it"
  if serial=$(serial_of "$name"); then
    adb -s "$serial" emu kill >/dev/null || true
    for _ in $(seq 30); do serial_of "$name" >/dev/null || break; sleep 1; done
    ! serial_of "$name" >/dev/null || die "$name ($serial) is still running"
  fi
  avdmanager delete avd -n "$name" >/dev/null
  echo "$name deleted"
}

cmd_list() {
  local ini name dir worktree state
  for ini in "${ANDROID_AVD_HOME:-$HOME/.android/avd}"/*.ini; do
    name=$(basename "$ini" .ini)
    owned "$name" || continue
    dir=$(avd_dir "$name")
    worktree=$(sed -n 's/^worktree=//p' "$dir/$owner_file")
    state=$(serial_of "$name" || echo stopped)
    [[ -d $worktree ]] || state="$state, worktree gone"
    printf '%s\t%s\t%s\n' "$name" "$state" "$worktree"
  done
}

cmd_prune() {
  local name state worktree
  while IFS=$'\t' read -r name state worktree; do
    case $state in
      'stopped, worktree gone') cmd_delete "$name" ;;
      *'worktree gone') echo "$name: worktree gone but still running ($state); close it in T3, then delete" ;;
    esac
  done < <(cmd_list)
}

command=${1:-}
name=${2:-}
[[ -n $name || $command == list || $command == prune ]] || name=$(branch_avd_name)

case $command in
  create) cmd_create "$name" ;;
  name) echo "$name" ;;
  serial) cmd_serial "$name" ;;
  setup) cmd_setup "$name" ;;
  delete) cmd_delete "$name" ;;
  list) cmd_list ;;
  prune) cmd_prune ;;
  *) die 'usage: scripts/emulator.sh <create|name|serial|setup|delete|list|prune> [avd-name]' ;;
esac
