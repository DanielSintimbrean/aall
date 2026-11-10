/** Departures from Cascada de les Moles (stop 126), read from the gateway behind Mou-te's web app. */
export type Departure = {
  line: string;
  destination: string;
  scheduled: number;
  realtime: number | null;
  located: boolean;
};

export const WINDOW_MINUTES = 120;
const MINUTE = 60_000;
const UNCONFIRMED_WITHIN = 30 * MINUTE;
const GATE = "https://feda.hafas.cloud/gate";
const STOP = "A=1@O=Cascada de les Moles - 126@X=1615568@Y=42572769@U=80@L=948@";
const wallClock = new Intl.DateTimeFormat("en-US", {
  timeZone: "Europe/Andorra",
  hourCycle: "h23",
  year: "numeric",
  month: "numeric",
  day: "numeric",
  hour: "numeric",
  minute: "numeric",
  second: "numeric",
});

type Product = { nameS?: string; prodCtx?: { line?: string } };
type Journey = {
  date: string;
  dirTxt?: string;
  prodX?: number;
  isCncl?: boolean;
  pos?: unknown;
  stbStop: { dProdX?: number; dTimeS: string; dTimeR?: string; dCncl?: boolean };
};
type Response = {
  svcResL: { err?: string; res?: { common?: { prodL?: Product[] }; jnyL?: Journey[] } }[];
};

export const time = (departure: Departure) => departure.realtime ?? departure.scheduled;

export async function fetchBoard(now: number): Promise<Departure[]> {
  const response = await fetch(GATE, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: request(now),
    signal: AbortSignal.timeout(15_000),
  });
  if (!response.ok) throw new Error(`Mou-te HTTP ${response.status}`);
  return parse(await response.text(), now);
}

export function request(now: number) {
  const moment = wall(now);
  return JSON.stringify({
    id: "aall",
    ver: "1.85",
    lang: "es",
    client: { id: "HAFAS", type: "WEB", name: "FEDA_webapp_prod", l: "vs_webapp" },
    auth: { type: "AID", aid: "nextgen" },
    svcReqL: [
      {
        meth: "StationBoard",
        req: {
          dur: WINDOW_MINUTES,
          stbLoc: { type: "S", lid: STOP, extId: "948" },
          type: "DEP",
          date: `${moment.getUTCFullYear()}${pad(moment.getUTCMonth() + 1)}${pad(moment.getUTCDate())}`,
          time: `${pad(moment.getUTCHours())}${pad(moment.getUTCMinutes())}${pad(moment.getUTCSeconds())}`,
          sort: "RT",
          maxJny: 40,
          getJnySameTime: true,
        },
      },
    ],
  });
}

export function parse(raw: string, now: number): Departure[] {
  const { svcResL }: Response = JSON.parse(raw);
  const result = svcResL[0];
  const response = result.res;
  if (!response) throw new Error(`Mou-te error: ${result.err ?? "no response"}`);
  const products = response.common?.prodL;
  const departures = (response.jnyL ?? [])
    .filter((journey) => !journey.isCncl && !journey.stbStop.dCncl)
    .map((journey): Departure => {
      const stop = journey.stbStop;
      const product = products?.[stop.dProdX ?? journey.prodX ?? -1];
      return {
        line: product?.prodCtx?.line || product?.nameS || "Bus",
        destination: journey.dirTxt ?? "",
        scheduled: millis(journey.date, stop.dTimeS),
        realtime: stop.dTimeR ? millis(journey.date, stop.dTimeR) : null,
        located: journey.pos != null,
      };
    });
  return upcoming(departures, now);
}

/** Also drops buses that left since the last fetch, so a failed refresh can still show what is ahead. */
export function upcoming(departures: Departure[], now: number) {
  return departures
    .filter((departure) => {
      const at = time(departure);
      return at > now - MINUTE && at <= now + WINDOW_MINUTES * MINUTE;
    })
    .sort((a, b) => time(a) - time(b));
}

export function source(departure: Departure, now: number) {
  if (departure.realtime != null) return "GPS";
  if (departure.located) return "Localizado";
  if (departure.scheduled - now < UNCONFIRMED_WITHIN) return "Sin confirmar";
  return null;
}

export function minutes(departure: Departure, now: number) {
  return Math.max(0, Math.floor((time(departure) - now) / MINUTE));
}

export function clock(at: number) {
  const moment = wall(at);
  return `${pad(moment.getUTCHours())}:${pad(moment.getUTCMinutes())}`;
}

/** Times are HHmmss, with two leading digits for a day offset when the journey crosses midnight. */
function millis(day: string, value: string) {
  const offset = value.length === 8 ? Number(value.slice(0, 2)) : 0;
  const hms = value.slice(-6);
  const local = Date.UTC(
    Number(day.slice(0, 4)),
    Number(day.slice(4, 6)) - 1,
    Number(day.slice(6, 8)) + offset,
    Number(hms.slice(0, 2)),
    Number(hms.slice(2, 4)),
    Number(hms.slice(4, 6)),
  );
  // The zone's offset near the answer, found from a first guess, in case a daylight saving change lies between.
  const guess = local - (wall(local).getTime() - local);
  return local - (wall(guess).getTime() - guess);
}

/** The wall clock at the stop, read through the UTC getters. */
function wall(at: number) {
  const parts = Object.fromEntries(
    wallClock.formatToParts(at).map(({ type, value }) => [type, Number(value)]),
  );
  return new Date(
    Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, parts.second),
  );
}

const pad = (value: number) => String(value).padStart(2, "0");
