export type Window = { start: string; end: string; days: number[] };
export type RoutineId = "access" | "bedtime" | "grayscale" | "focus";
export type RoutineStatus = "disabled" | "needsSetup" | "active" | "ready";
export type Routine = {
  id: RoutineId;
  title: string;
  enabled: boolean;
  windows: Window[];
  apps: string[];
  colorApps: string[];
  earlyStart: string;
};
export type Config = { version: 1; focusMinutes: number; routines: Routine[] };
export type InstalledApp = { packageName: string; label: string };
export type Snapshot = {
  config: Config;
  active: string[];
  dayOff: boolean;
  daysOff: string[];
  focusUntil: number;
  pauseUntil: number;
  nextChange: number;
  permissions: { accessibility: boolean; dnd: boolean; alarms: boolean; grayscale: boolean };
  errors: string[];
  statuses: Record<RoutineId, RoutineStatus>;
  overrides: Partial<Record<RoutineId, Routine>>;
};
export const statusLabels: Record<RoutineStatus, string> = {
  disabled: "Disabled",
  needsSetup: "Needs setup",
  active: "Active",
  ready: "Ready",
};
type Editor = {
  hint?: string;
  windowLabel: string;
  cardPrefix?: string;
  windowCountEditable: boolean;
  noHoursWarning?: string;
  session: boolean;
  chargingStart: boolean;
  appList?: "Allowed" | "Blocked";
  colorExceptions: boolean;
};
export const editors: Record<RoutineId, Editor> = {
  access: {
    hint: "Selected apps are blocked outside allowed hours.",
    windowLabel: "Allowed hours",
    cardPrefix: "Allowed ",
    windowCountEditable: true,
    noHoursWarning: "With no allowed hours, the selected apps stay blocked all day.",
    session: false,
    chargingStart: false,
    appList: "Blocked",
    colorExceptions: false,
  },
  focus: {
    hint: "Blocks the selected apps with Do Not Disturb and grayscale while a session runs.",
    windowLabel: "Active hours",
    windowCountEditable: false,
    session: true,
    chargingStart: false,
    appList: "Blocked",
    colorExceptions: true,
  },
  bedtime: {
    hint: "Apps outside the allowed list are blocked, with Do Not Disturb and grayscale.",
    windowLabel: "Active hours",
    windowCountEditable: false,
    session: false,
    chargingStart: true,
    appList: "Allowed",
    colorExceptions: true,
  },
  grayscale: {
    hint: "The screen turns gray during active hours, except in apps that keep color.",
    windowLabel: "Active hours",
    windowCountEditable: true,
    noHoursWarning: "With no active hours, grayscale never turns on.",
    session: false,
    chargingStart: false,
    colorExceptions: true,
  },
};
const all = [1, 2, 3, 4, 5, 6, 7];
const social = [
  "com.instagram.android",
  "com.zhiliaoapp.musically",
  "com.ss.android.ugc.trill",
  "com.google.android.youtube",
  "org.schabi.newpipe",
  "com.twitter.android",
  "com.reddit.frontpage",
  "com.facebook.katana",
  "com.instagram.barcelona",
];
const colorApps = [
  "com.google.android.GoogleCamera",
  "app.grapheneos.camera",
  "com.sec.android.app.camera",
  "com.google.android.apps.photos",
  "com.google.android.apps.maps",
];
export const defaults: Config = {
  version: 1,
  focusMinutes: 50,
  routines: [
    {
      id: "access",
      title: "App access",
      enabled: false,
      windows: [
        { start: "12:30", end: "13:15", days: all },
        { start: "19:00", end: "20:00", days: all },
      ],
      apps: social,
      colorApps: [],
      earlyStart: "20:00",
    },
    {
      id: "focus",
      title: "Focus",
      enabled: false,
      windows: [],
      apps: social,
      colorApps,
      earlyStart: "20:00",
    },
    {
      id: "bedtime",
      title: "Bedtime",
      enabled: false,
      windows: [{ start: "22:30", end: "07:00", days: all }],
      apps: [
        "com.android.deskclock",
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.whatsapp",
        "com.spotify.music",
        "com.google.android.apps.maps",
      ],
      colorApps,
      earlyStart: "20:00",
    },
    {
      id: "grayscale",
      title: "Grayscale",
      enabled: false,
      windows: [
        { start: "09:00", end: "13:00", days: [1, 2, 3, 4, 5] },
        { start: "14:00", end: "18:00", days: [1, 2, 3, 4, 5] },
        { start: "21:00", end: "07:00", days: all },
      ],
      apps: [],
      colorApps,
      earlyStart: "20:00",
    },
  ],
};
export function validTime(value: string) {
  return /^([01]\d|2[0-3]):[0-5]\d$/.test(value);
}
export function localDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
export function parseDate(value: string): Date | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const [y, m, d] = value.split("-").map(Number);
  const result = new Date(y, m - 1, d);
  return localDate(result) === value ? result : null;
}
export function canBookDay(value: string, now = Date.now()) {
  const date = parseDate(value);
  return !!date && date.getTime() - now >= 72 * 60 * 60 * 1000;
}
export function earliestDay(now = Date.now()) {
  const date = new Date(now + 72 * 60 * 60 * 1000);
  date.setHours(0, 0, 0, 0);
  if (date.getTime() < now + 72 * 60 * 60 * 1000) date.setDate(date.getDate() + 1);
  return localDate(date);
}

export const weekdays = [
  { id: 1, short: "M", name: "Monday" },
  { id: 2, short: "T", name: "Tuesday" },
  { id: 3, short: "W", name: "Wednesday" },
  { id: 4, short: "T", name: "Thursday" },
  { id: 5, short: "F", name: "Friday" },
  { id: 6, short: "S", name: "Saturday" },
  { id: 7, short: "S", name: "Sunday" },
] as const;
export const earliestChargingStart = "20:00";
export function minutesLeft(until: number, now = Date.now()) {
  return Math.max(0, Math.ceil((until - now) / 60000));
}
export function timeToDate(value: string, base = new Date()) {
  const [hours, minutes] = value.split(":").map(Number);
  const date = new Date(base);
  date.setHours(hours, minutes, 0, 0);
  return date;
}
export function dateToTime(date: Date) {
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}
export function isOvernight(window: Window) {
  return window.end <= window.start;
}
export function daysLabel(days: number[]) {
  const set = [...days].sort().join("");
  if (set === "1234567") return "Every day";
  if (set === "12345") return "Weekdays";
  if (set === "67") return "Weekends";
  return weekdays
    .filter((day) => days.includes(day.id))
    .map((day) => day.name.slice(0, 3))
    .join(", ");
}
export function windowsLabel(windows: Window[]) {
  if (!windows.length) return "No hours set";
  const shown = windows.length > 2 ? windows.slice(0, 1) : windows;
  const times = shown.map((w) => `${w.start}–${w.end}`);
  if (windows.length > shown.length) times.push(`+${windows.length - shown.length} more`);
  return times.join(" · ");
}
export function parseMinutes(value: string) {
  const minutes = Number(value);
  return /^\d+$/.test(value) && minutes >= 1 && minutes <= 1440 ? minutes : null;
}
export function dateLabel(value: string) {
  const date = parseDate(value);
  return date
    ? date.toLocaleDateString("en-GB", { weekday: "long", day: "numeric", month: "long" })
    : value;
}
export function clockLabel(timestamp: number, now = Date.now()) {
  const date = new Date(timestamp);
  const time = dateToTime(date);
  if (localDate(date) === localDate(new Date(now))) return time;
  return `${date.toLocaleDateString("en-GB", { weekday: "short" })} ${time}`;
}
export function errorMessage(error: unknown) {
  const text = error instanceof Error ? error.message : String(error);
  const cause = text.split("Caused by:").pop()?.trim();
  return (cause || text).replace(/^(java\.lang\.\w+|Error):\s*/, "");
}
