export type Window = { start: string; end: string; days: number[] };
export type Routine = {
  id: "access" | "bedtime" | "grayscale" | "focus";
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
