import limits from "./limits.json";
import { editors, parseMinutes, type Routine, type Window } from "./model";

/** The unsaved copy of a routine that the editor changes. `minutes` is the focus duration as typed. */
export type Draft = {
  routine: Routine;
  minutes: string;
};

export type DraftEdit =
  | { type: "setEnabled"; enabled: boolean }
  | { type: "setMinutes"; value: string }
  | { type: "stepMinutes"; by: number }
  | { type: "setWindowTime"; index: number; time: Partial<Pick<Window, "start" | "end">> }
  | { type: "toggleWindowDay"; index: number; day: number }
  | { type: "addWindow" }
  | { type: "removeWindow"; index: number }
  | { type: "setEarlyStart"; earlyStart: string }
  | { type: "setRamp"; field: RampField; value: string }
  | { type: "stepRamp"; field: RampField; by: number }
  | { type: "toggleApp"; list: "apps" | "colorApps"; packageName: string };

export type RampField = "rampMinutes" | "finalIntensity";

export function beginDraft(routine: Routine, minutes: number): Draft {
  return {
    // oxlint-disable-next-line typescript/no-unsafe-type-assertion -- JSON copy of a Routine.
    routine: JSON.parse(JSON.stringify(routine)) as Routine,
    minutes: String(minutes),
  };
}

function toggled<T>(list: T[], item: T) {
  return list.includes(item) ? list.filter((other) => other !== item) : [...list, item];
}

function editRoutine(draft: Draft, change: Partial<Routine>): Draft {
  return { ...draft, routine: { ...draft.routine, ...change } };
}

function editWindow(draft: Draft, index: number, edit: (window: Window) => Window) {
  return editRoutine(draft, {
    windows: draft.routine.windows.map((window, i) => (i === index ? edit(window) : window)),
  });
}

export function editDraft(draft: Draft, edit: DraftEdit): Draft {
  switch (edit.type) {
    case "setEnabled":
      return editRoutine(draft, { enabled: edit.enabled });
    case "setMinutes":
      return { ...draft, minutes: edit.value.replace(/\D/g, "") };
    case "stepMinutes": {
      const current = Number(draft.minutes) || 0;
      const { min, max } = limits.focusMinutes;
      return { ...draft, minutes: String(Math.min(max, Math.max(min, current + edit.by))) };
    }
    case "setWindowTime":
      return editWindow(draft, edit.index, (window) => ({ ...window, ...edit.time }));
    case "toggleWindowDay":
      return editWindow(draft, edit.index, (window) => ({
        ...window,
        days: toggled(window.days, edit.day).sort((a, b) => a - b),
      }));
    case "addWindow":
      return editRoutine(draft, {
        windows: [
          ...draft.routine.windows,
          { start: "09:00", end: "17:00", days: [1, 2, 3, 4, 5] },
        ],
      });
    case "removeWindow":
      return editRoutine(draft, {
        windows: draft.routine.windows.filter((_, i) => i !== edit.index),
      });
    case "setEarlyStart":
      return editRoutine(draft, { earlyStart: edit.earlyStart });
    case "setRamp":
      return editRoutine(draft, { [edit.field]: Number(edit.value.replace(/\D/g, "")) });
    case "stepRamp": {
      const { min, max } = limits[edit.field];
      const value = draft.routine[edit.field] + edit.by;
      return editRoutine(draft, { [edit.field]: Math.min(max, Math.max(min, value)) });
    }
    default:
      return editRoutine(draft, {
        [edit.list]: toggled(draft.routine[edit.list], edit.packageName),
      });
  }
}

/** Leaving the editor asks to discard only when this is true. An edit that was undone doesn't count. */
export function isDirty(draft?: Draft, original?: Draft) {
  return JSON.stringify(draft) !== JSON.stringify(original);
}

export function rampFieldInvalid(routine: Routine, field: RampField) {
  const { min, max } = limits[field];
  return routine[field] < min || routine[field] > max;
}

/** Validates what the routine kind's editor shows, and says which saves are open. */
export function checkDraft(draft: Draft, original: Draft | undefined, overridden: boolean) {
  const { routine } = draft;
  const editor = editors[routine.id];
  const minutes = parseMinutes(draft.minutes);
  const earlyStartInvalid =
    editor.chargingStart && routine.earlyStart < limits.earliestChargingStart;
  const invalid =
    routine.windows.some((window) => !window.days.length) ||
    earlyStartInvalid ||
    (editor.session && minutes === null) ||
    (!!editor.ramp &&
      (rampFieldInvalid(routine, "rampMinutes") || rampFieldInvalid(routine, "finalIntensity")));
  const dirty = isDirty(draft, original);
  return {
    minutes,
    earlyStartInvalid,
    canSaveToday: dirty && !invalid,
    // Saving weekly with no edits still ends an override.
    canSaveWeekly: (dirty || overridden) && !invalid,
  };
}

/** The focus duration a weekly save must write, or null when the saved one still holds. */
export function focusMinutesToSave(draft: Draft, savedMinutes: number) {
  const minutes = parseMinutes(draft.minutes);
  return editors[draft.routine.id].session && minutes !== savedMinutes ? minutes : null;
}
