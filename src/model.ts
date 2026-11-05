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
  earliestDayOff: string;
  focusUntil: number;
  turningOff: Partial<Record<RoutineId, number>>;
  pauseUntil: number;
  nextChange: number;
  permissions: {
    accessibility: boolean;
    dnd: boolean;
    alarms: boolean;
    notifications: boolean;
    grayscale: boolean;
  };
  errors: string[];
  statuses: Record<RoutineId, RoutineStatus>;
  overrides: Partial<Record<RoutineId, Routine>>;
};
export const statusLabels: Record<RoutineStatus, string> = {
  disabled: "Desactivada",
  needsSetup: "Requiere configuración",
  active: "Activa",
  ready: "Lista",
};
type Editor = {
  hint?: string;
  windowLabel: string;
  cardPrefix?: string;
  windowCountEditable: boolean;
  noHoursWarning?: string;
  session: boolean;
  chargingStart: boolean;
  appList?: "allowed" | "blocked";
  colorExceptions: boolean;
};
export const editors: Record<RoutineId, Editor> = {
  access: {
    hint: "Las aplicaciones seleccionadas se bloquean fuera del horario permitido.",
    windowLabel: "Horario permitido",
    cardPrefix: "Permitido ",
    windowCountEditable: true,
    noHoursWarning:
      "Sin un horario permitido, las aplicaciones seleccionadas permanecen bloqueadas todo el día.",
    session: false,
    chargingStart: false,
    appList: "blocked",
    colorExceptions: false,
  },
  focus: {
    hint: "Bloquea las aplicaciones seleccionadas y activa No molestar y la escala de grises durante la sesión.",
    windowLabel: "Horario activo",
    windowCountEditable: false,
    session: true,
    chargingStart: false,
    appList: "blocked",
    colorExceptions: true,
  },
  bedtime: {
    hint: "Bloquea las aplicaciones que no están permitidas y activa No molestar y la escala de grises.",
    windowLabel: "Horario activo",
    windowCountEditable: false,
    session: false,
    chargingStart: true,
    appList: "allowed",
    colorExceptions: true,
  },
  grayscale: {
    hint: "La pantalla se muestra en gris durante el horario activo, excepto en las aplicaciones que conservan el color.",
    windowLabel: "Horario activo",
    windowCountEditable: true,
    noHoursWarning: "Sin un horario activo, la escala de grises nunca se activa.",
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
      title: "Acceso a aplicaciones",
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
      title: "Concentración",
      enabled: false,
      windows: [],
      apps: social,
      colorApps,
      earlyStart: "20:00",
    },
    {
      id: "bedtime",
      title: "Descanso",
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
      title: "Escala de grises",
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

export const weekdays = [
  { id: 1, short: "L", name: "lunes" },
  { id: 2, short: "M", name: "martes" },
  { id: 3, short: "X", name: "miércoles" },
  { id: 4, short: "J", name: "jueves" },
  { id: 5, short: "V", name: "viernes" },
  { id: 6, short: "S", name: "sábado" },
  { id: 7, short: "D", name: "domingo" },
] as const;
export const earliestChargingStart = "20:00";
export function minutesLeft(until: number, now = Date.now()) {
  return Math.max(0, Math.ceil((until - now) / 60000));
}
export function focusMinutesLeft(state: Pick<Snapshot, "focusUntil" | "active">, now = Date.now()) {
  return state.active.includes("focus") ? minutesLeft(state.focusUntil, now) : 0;
}
export function timeToDate(value: string) {
  const [hours, minutes] = value.split(":").map(Number);
  // A fixed winter date, because today could be missing this time at a clock change.
  return new Date(2000, 0, 1, hours, minutes);
}
export function dateToTime(date: Date) {
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}
export function isOvernight(window: Window) {
  return window.end <= window.start;
}
export function daysLabel(days: number[]) {
  const set = [...days].sort((a, b) => a - b).join("");
  if (set === "1234567") return "Todos los días";
  if (set === "12345") return "De lunes a viernes";
  if (set === "67") return "Fines de semana";
  return weekdays
    .filter((day) => days.includes(day.id))
    .map((day) => day.name.slice(0, 3))
    .join(", ");
}
export function windowsLabel(windows: Window[]) {
  if (!windows.length) return "Sin horario";
  const shown = windows.length > 2 ? windows.slice(0, 1) : windows;
  const times = shown.map((w) => `${w.start}–${w.end}`);
  if (windows.length > shown.length) times.push(`+${windows.length - shown.length} más`);
  return times.join(" · ");
}
export function parseMinutes(value: string) {
  const minutes = Number(value);
  return /^\d+$/.test(value) && minutes >= 1 && minutes <= 1440 ? minutes : null;
}
export function dateLabel(value: string) {
  const date = parseDate(value);
  return date
    ? date.toLocaleDateString("es-ES", { weekday: "long", day: "numeric", month: "long" })
    : value;
}
export function clockLabel(timestamp: number, now = Date.now()) {
  const date = new Date(timestamp);
  const time = dateToTime(date);
  if (localDate(date) === localDate(new Date(now))) return time;
  return `${date.toLocaleDateString("es-ES", { weekday: "short" })} ${time}`;
}
export function errorMessage(error: unknown) {
  const text = error instanceof Error ? error.message : String(error);
  if (text.includes("Reserva con al menos 72 horas"))
    return "Reserva con al menos 72 horas de antelación a medianoche.";
  if (text.includes("Activa primero Concentración")) return "Activa primero Concentración.";
  return "No se ha podido completar la acción. Revisa los permisos e inténtalo de nuevo.";
}
