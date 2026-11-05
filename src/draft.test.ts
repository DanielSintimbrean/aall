import { describe, expect, it } from "vite-plus/test";
import {
  beginDraft,
  checkDraft,
  editDraft,
  focusMinutesToSave,
  isDirty,
  type DraftEdit,
} from "./draft";
import { defaults, type RoutineId } from "./model";

const begin = (id: RoutineId) => {
  const routine = defaults.routines.find((r) => r.id === id);
  if (!routine) throw Error(`No default ${id}`);
  return beginDraft(routine, 50);
};
const apply = (id: RoutineId, ...edits: DraftEdit[]) => edits.reduce(editDraft, begin(id));
const step = (value: string, by: number) =>
  apply("focus", { type: "setMinutes", value }, { type: "stepMinutes", by }).minutes;

describe("draft edits", () => {
  it("keeps the night light ramp inside its limits", () => {
    const low = apply("nightLight", { type: "stepRamp", field: "rampMinutes", by: -500 });
    expect(low.routine.rampMinutes).toBe(15);
    const typed = editDraft(low, { type: "setRamp", field: "finalIntensity", value: "1x00" });
    expect(typed.routine.finalIntensity).toBe(100);
    const high = editDraft(typed, { type: "stepRamp", field: "finalIntensity", by: 5 });
    expect(high.routine.finalIntensity).toBe(100);
  });
  it("won't save a night light ramp outside its limits", () => {
    const original = begin("nightLight");
    const short = editDraft(original, { type: "setRamp", field: "rampMinutes", value: "4" });
    expect(checkDraft(short, original, false).canSaveWeekly).toBe(false);
    const empty = editDraft(original, { type: "setRamp", field: "finalIntensity", value: "" });
    expect(checkDraft(empty, original, false).canSaveToday).toBe(false);
    const good = editDraft(original, { type: "setRamp", field: "finalIntensity", value: "75" });
    expect(checkDraft(good, original, false).canSaveWeekly).toBe(true);
  });
  it("copies the routine, so edits leave the saved one alone", () => {
    const draft = begin("access");
    expect(draft.minutes).toBe("50");
    draft.routine.windows[0].days.pop();
    expect(defaults.routines[0].windows[0].days).toHaveLength(7);
  });
  it("changes only the targeted window", () => {
    const { windows } = apply("access", {
      type: "setWindowTime",
      index: 1,
      time: { end: "21:00" },
    }).routine;
    expect(windows[0]).toEqual({ ...defaults.routines[0].windows[0] });
    expect(windows[1]).toMatchObject({ start: "19:00", end: "21:00" });
  });
  it("keeps weekdays in order when one is toggled back on", () => {
    const off = apply("grayscale", { type: "toggleWindowDay", index: 0, day: 2 });
    expect(off.routine.windows[0].days).toEqual([1, 3, 4, 5]);
    const on = editDraft(off, { type: "toggleWindowDay", index: 0, day: 2 });
    expect(on.routine.windows[0].days).toEqual([1, 2, 3, 4, 5]);
  });
  it("adds weekday working hours and removes by position", () => {
    const added = apply("access", { type: "addWindow" }).routine.windows;
    expect(added[2]).toEqual({ start: "09:00", end: "17:00", days: [1, 2, 3, 4, 5] });
    const removed = apply("access", { type: "removeWindow", index: 0 }).routine.windows;
    expect(removed.map((w) => w.start)).toEqual(["19:00"]);
  });
  it("toggles apps in the named list only", () => {
    const { apps, colorApps } = apply("focus", {
      type: "toggleApp",
      list: "colorApps",
      packageName: "a.b",
    }).routine;
    expect(colorApps.at(-1)).toBe("a.b");
    expect(apps).toEqual(begin("focus").routine.apps);
    const off = apply(
      "focus",
      { type: "toggleApp", list: "apps", packageName: "a.b" },
      { type: "toggleApp", list: "apps", packageName: "a.b" },
    );
    expect(off.routine.apps).toEqual(begin("focus").routine.apps);
  });
  it("keeps only digits in typed minutes", () => {
    expect(apply("focus", { type: "setMinutes", value: "4-5." }).minutes).toBe("45");
    expect(apply("focus", { type: "setMinutes", value: "" }).minutes).toBe("");
  });
  it("steps minutes within 1 to 1440, starting from empty", () => {
    expect(step("50", 5)).toBe("55");
    expect(step("", 5)).toBe("5");
    expect(step("3", -5)).toBe("1");
    expect(step("1439", 5)).toBe("1440");
  });
});

describe("discard check", () => {
  const original = begin("bedtime");
  it("is clean until an edit, and clean again once it is undone", () => {
    expect(isDirty(begin("bedtime"), original)).toBe(false);
    const edited = editDraft(original, { type: "toggleWindowDay", index: 0, day: 3 });
    expect(isDirty(edited, original)).toBe(true);
    const undone = editDraft(edited, { type: "toggleWindowDay", index: 0, day: 3 });
    expect(isDirty(undone, original)).toBe(false);
  });
  it("counts a focus duration edit", () =>
    expect(isDirty(editDraft(original, { type: "setMinutes", value: "25" }), original)).toBe(true));
});

describe("draft validation and saves", () => {
  it("offers no save for an unedited routine", () => {
    const draft = begin("access");
    const check = checkDraft(draft, draft, false);
    expect(check.canSaveToday).toBe(false);
    expect(check.canSaveWeekly).toBe(false);
  });
  it("offers both saves for a valid edit", () => {
    const check = checkDraft(
      apply("access", { type: "setEnabled", enabled: true }),
      begin("access"),
      false,
    );
    expect(check.canSaveToday).toBe(true);
    expect(check.canSaveWeekly).toBe(true);
  });
  it("lets an override end with a weekly save and no edits", () => {
    const draft = begin("access");
    const check = checkDraft(draft, draft, true);
    expect(check.canSaveToday).toBe(false);
    expect(check.canSaveWeekly).toBe(true);
  });
  it("blocks saving a window with no days", () => {
    const edits = [1, 2, 3, 4, 5, 6, 7].map((day): DraftEdit => ({
      type: "toggleWindowDay",
      index: 0,
      day,
    }));
    const check = checkDraft(apply("access", ...edits), begin("access"), true);
    expect(check.canSaveToday).toBe(false);
    expect(check.canSaveWeekly).toBe(false);
  });
  it("checks an early start only for kinds with a charging start", () => {
    const early = { type: "setEarlyStart", earlyStart: "19:59" } as const;
    const bedtime = checkDraft(apply("bedtime", early), begin("bedtime"), false);
    expect(bedtime.earlyStartInvalid).toBe(true);
    expect(bedtime.canSaveWeekly).toBe(false);
    const edge = { ...early, earlyStart: "20:00" };
    expect(checkDraft(apply("bedtime", edge), begin("bedtime"), false).earlyStartInvalid).toBe(
      false,
    );
    const access = checkDraft(apply("access", early), begin("access"), false);
    expect(access.earlyStartInvalid).toBe(false);
    expect(access.canSaveWeekly).toBe(true);
  });
  it("checks minutes only for session kinds", () => {
    const bad = { type: "setMinutes", value: "0" } as const;
    const focus = checkDraft(apply("focus", bad), begin("focus"), false);
    expect(focus.minutes).toBeNull();
    expect(focus.canSaveWeekly).toBe(false);
    expect(checkDraft(apply("access", bad), begin("access"), false).canSaveWeekly).toBe(true);
  });
  it("writes the focus duration only when it changed on a session kind", () => {
    const edit = { type: "setMinutes", value: "25" } as const;
    expect(focusMinutesToSave(apply("focus", edit), 50)).toBe(25);
    expect(focusMinutesToSave(begin("focus"), 50)).toBeNull();
    expect(focusMinutesToSave(apply("access", edit), 50)).toBeNull();
  });
});
