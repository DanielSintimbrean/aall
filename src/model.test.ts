import { describe, expect, it, vi } from "vite-plus/test";
import {
  canBookDay,
  clockLabel,
  dateToTime,
  focusMinutesLeft,
  daysLabel,
  defaults,
  earliestDay,
  errorMessage,
  isOvernight,
  parseDate,
  parseMinutes,
  timeToDate,
  validTime,
  windowsLabel,
} from "./model";
describe("phone configuration", () => {
  it("starts every routine disabled", () =>
    expect(defaults.routines.every((r) => !r.enabled)).toBe(true));
  it("validates dates and times without normalization", () => {
    expect(parseDate("2026-02-30")).toBeNull();
    expect(validTime("24:00")).toBe(false);
    expect(validTime("09:05")).toBe(true);
  });
  it("requires 72 elapsed hours, inclusive", () => {
    const date = new Date(2026, 10, 10).getTime();
    expect(canBookDay("2026-11-10", date - 72 * 3600000)).toBe(true);
    expect(canBookDay("2026-11-10", date - 72 * 3600000 + 1)).toBe(false);
  });
  it("offers an eligible local midnight", () => {
    const now = new Date(2026, 2, 27, 12).getTime();
    expect(canBookDay(earliestDay(now), now)).toBe(true);
  });
});

describe("screen labels", () => {
  it("names common day sets and truncates long schedules", () => {
    expect(daysLabel([1, 2, 3, 4, 5, 6, 7])).toBe("Every day");
    expect(daysLabel([5, 4, 3, 2, 1])).toBe("Weekdays");
    expect(daysLabel([1, 3])).toBe("Mon, Wed");
    const w = { start: "09:00", end: "10:00", days: [1] };
    expect(windowsLabel([w, w, w])).toBe("09:00–10:00 · +2 more");
  });
  it("treats an end at or before the start as overnight", () => {
    expect(isOvernight({ start: "22:30", end: "07:00", days: [1] })).toBe(true);
    expect(isOvernight({ start: "09:00", end: "17:00", days: [1] })).toBe(false);
  });
  it("accepts only whole minutes from 1 to 1440", () => {
    expect(parseMinutes("50")).toBe(50);
    for (const bad of ["", "0", "1441", "1.5", "-5"]) expect(parseMinutes(bad)).toBeNull();
  });
  it("omits the weekday for clock times today", () => {
    const now = new Date(2026, 10, 4, 12).getTime();
    expect(clockLabel(new Date(2026, 10, 4, 19).getTime(), now)).toBe("19:00");
    expect(clockLabel(new Date(2026, 10, 5, 7).getTime(), now)).toBe("Thu 07:00");
  });
  it("shows the cause of a native failure", () => {
    const failure = Error(
      "Calling the 'bookDay' function has failed\n→ Caused by: java.lang.IllegalArgumentException: Book at least 72 elapsed hours before midnight.",
    );
    expect(errorMessage(failure)).toBe("Book at least 72 elapsed hours before midnight.");
    expect(errorMessage(Error("Plain"))).toBe("Plain");
  });
  it("keeps a time inside the hour skipped by a clock change", () => {
    const zone = process.env.TZ;
    process.env.TZ = "Europe/Madrid";
    vi.useFakeTimers({ now: new Date(2026, 2, 29, 12) });
    try {
      expect(dateToTime(timeToDate("02:30"))).toBe("02:30");
    } finally {
      vi.useRealTimers();
      process.env.TZ = zone;
    }
  });
  it("counts focus only while the routine is running", () => {
    const now = 1000;
    const focusUntil = now + 10 * 60000;
    expect(focusMinutesLeft({ focusUntil, active: ["focus"] }, now)).toBe(10);
    expect(focusMinutesLeft({ focusUntil, active: [] }, now)).toBe(0);
  });
});
