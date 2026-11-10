# aall

aall is my personal Android app for routines: app blocking, focus, bedtime, grayscale and Night Light, all edited on the phone. It runs on one phone, mine, and agents write most of it.

## Principles

I like simple systems and software that feels obvious. Existing complexity earns no protection, so delete what the behavior doesn't need. Build for the request in front of you, not for one that might come later (yagni). Find the real constraint, then fight for the smallest model that makes the behavior unsurprising.

Measure twice, cut once. Honor the request in a minimal and realistic way. What follows are good defaults, and my instructions override any of them. If a rule fights the task, say so and ask before breaking it.

## Ways to hurt yourself

1. **The physical phone.** `3B291FDJG004K7` is my real phone, and I may be using it while you work. Test on your branch's own emulator through the [android-emulator skill](.agents/skills/android-emulator/SKILL.md) and delete it when you are done. Use the phone only when behavior must be proven on real hardware. Leave other branches' emulators to their sessions.
2. **Lost settings.** Settings exist only on the phone, and uninstalling aall deletes them. So does installing a build signed with a different key, because Android makes you uninstall first. On the phone, install over the existing app with `adb install -r` and an APK signed with the release key. `pnpm build:apk` alone signs with Expo's development key.
3. **Release identity.** Android accepts an update only when the release key in `~/.local/share/aall/signing/` signed it, and Obtainium follows release tags. Never recreate the keystore or reuse a tag.
4. **Test tools that switch aall off.** `uiautomator dump` and agent-device's snapshot helper suspend every accessibility service, so aall stops blocking and reports accessibility missing. Force-stopping aall, including `agent-device open --relaunch`, removes its service from `enabled_accessibility_services`. The emulator skill says how to recover.

## How it works

The React UI in `App.tsx` and `src/` is dumb: it polls a read-only snapshot and sends requests. The Kotlin module in `modules/aall-automation/android/src/main/java/dev/danields/aall/` owns everything else. `Reconcile.decide` is pure and owns every rule: it takes the stored state and an `Environment` (clock, foreground app, charging, permissions) and returns a decision. `Engine` loads state, decides, saves, and applies the effects to Android: DND, grayscale, Night Light, alarms and the accessibility overlay. Alarms, broadcasts and accessibility events run it without React. [Glossary](docs/internals/glossary.md) defines the words, [design](docs/internals/design.md) the agreed behavior, and [Android quirks](docs/internals/android-quirks.md) what Android does that its docs don't say, plus what still needs proving on the phone.

## Verifying and testing

- Run `mise run check` before you commit: typecheck, Vite+ and ktlint formatting and lint, and the TypeScript tests.
- The native JVM tests run only inside `pnpm build:apk`. A faster standalone command is planned; until then, use the build.
- Pure code takes `Clock`, `Environment` or plain data and returns values. Reading the clock or Android belongs in `Engine` and the services. `NightLight.advance` is the model: `Engine` reads the settings, `advance` decides, `Engine` writes.
- A logic or state bug fix starts with a test that goes red on main, at the lowest layer where it fails. When the bug lives in an adapter (`Engine`, `AallAccessibilityService`), first extract the decision into a pure function, then test that.
- Extend before you add. When an existing case or table covers the behavior, add a row or an assertion there.
- Test behavior that can break. Copy text, labels, what types already guarantee and trivial wiring get no test.
- A visual bug's proof is a before/after screenshot, not a test.
- On a device, assert on state: `adb shell settings get`, `dumpsys`. Screenshots don't show grayscale or Night Light.

## Taste

- Default to no comment. Write one for a constraint, an external quirk or an invariant the code can't show, in one line where you can and four at most. Names, step-by-step narration and history stay out; the PR carries history.
- Native owns every limit and rule. The UI shows what native decided and never re-implements a rule.
- React effects only sync with something outside React. Derive in render and act in handlers.

## Documentation

Most changes need no doc change. Agents can read the code.

- When a fact changes, rewrite or delete the old text. Docs describe the current app; PR summaries and test logs belong in the PR.
- `docs/internals/` holds the design, the glossary and Android quirks. Agreed behavior goes in `design.md`, shared words in `glossary.md`, and a device-specific Android fact in `android-quirks.md`. A local reason goes in a short code comment.
- The root holds only README.md and AGENTS.md.

## Pull requests

- Open a PR only when I ask. One request is one PR.
- Merge with squash: `gh pr merge --squash`.
- Title: a conventional commit in plain language, such as `fix: bedtime no longer blocks the launcher`.
- Body: follow [the template](.github/pull_request_template.md). The problem, the change, and verification: what you ran and observed, and what you couldn't check.
- UI changes need before/after screenshots.
- End with the model and harness that did the work.
