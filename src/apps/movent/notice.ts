import { clock, type Departure, minutes, source, time, upcoming } from "./board";

export type Notice = { title: string; text: string; lines: string[]; offline: boolean };

const MAX_LINES = 5;

/** `departures` is undefined until the first fetch has finished. */
export function notice(departures: Departure[] | undefined, offline: boolean, now: number): Notice {
  const ahead = departures && upcoming(departures, now);
  const next = ahead?.[0];
  if (!ahead || !next) {
    return {
      title: "Movent",
      text:
        ahead && !offline
          ? "Sin buses en las próximas 2 horas"
          : offline
            ? "Sin conexión"
            : "Buscando buses…",
      lines: [],
      offline: false,
    };
  }
  const left = minutes(next, now);
  return {
    title: `${next.line} · ${clock(time(next))} · ${left === 0 ? "ahora" : `en ${left} min`}`,
    text: [next.destination, source(next, now)].filter(Boolean).join(" · "),
    lines: ahead
      .slice(1, 1 + MAX_LINES)
      .map((departure) =>
        [
          `${clock(time(departure))} ${departure.line}`,
          departure.destination,
          source(departure, now),
        ]
          .filter(Boolean)
          .join(" · "),
      ),
    offline,
  };
}
