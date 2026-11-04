# Validation

2026-11-04, Pixel 8 Pro running GrapheneOS Android 17, SDK 37.

## Passed

- Expo dependency compatibility check.
- TypeScript strict typecheck, Vite+ formatting and lint.
- Four shared TypeScript tests for defaults, input validation and 72-hour booking boundaries.
- Nine native JVM tests for overnight ownership, Only today, elapsed timers and reboot deadlines, overlapping requests, bypass suppression and expiry, retry verification, DST booking boundaries, timezone-aware temporary expiry and 10,000 generated arithmetic challenges.
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

## Remaining verification

- Reboot, timezone and clock changes on the physical phone.
- Overnight and charging-triggered bedtime through wake-up time on the physical phone.
- Day-off midnight transitions and a full 15-minute bypass while the device is idle.
- Color exceptions while Camera, Photos and Maps are foregrounded.
- Obtainium update installation.

These checks require real elapsed time or changes to the phone's operating state. JVM tests cover the rule boundaries but do not prove Android delivery under every battery or idle condition.

## Tooling warnings

Vite+ 1.5.0 uses its bundled Vite implementation. pnpm reports Vitest's upstream Vite version range against the Vite+ alias version. The shared tests run successfully. Expo's native Gradle plugins emit obsolete variant API warnings under AGP 9.2.1; native build succeeds. These dependencies will need updates before AGP 10.
