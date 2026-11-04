import { describe, expect, it } from "vite-plus/test";
import { canBookDay, defaults, earliestDay, parseDate, validTime } from "./model";
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
