import { describe, expect, test } from "vite-plus/test";
import { type Departure, parse, request, source, time } from "./board";
import { notice } from "./notice";

// Andorra is on UTC+1 at every time these tests use.
const at = (hour: number, minute: number, day = 5, month = 11) =>
  Date.UTC(2026, month - 1, day, hour - 1, minute);
const now = at(12, 50);

function journey(
  scheduled: string,
  { realtime, position = false, cancelled = false, product = 0 } = {} as {
    realtime?: string;
    position?: boolean;
    cancelled?: boolean;
    product?: number;
  },
) {
  return {
    date: "20261105",
    dirTxt: "Andorra la Vella",
    prodX: product,
    isCncl: cancelled,
    pos: position ? { x: 1, y: 2 } : null,
    stbStop: { dProdX: product, dTimeS: scheduled, ...(realtime && { dTimeR: realtime }) },
  };
}

const response = (...journeys: object[]) =>
  JSON.stringify({
    svcResL: [
      {
        err: "OK",
        res: {
          common: { prodL: [{ nameS: "L3", prodCtx: { line: "L3" } }, { nameS: "L4" }] },
          jnyL: journeys,
        },
      },
    ],
  });

const departure = (
  scheduled: number,
  { realtime = null, located = false }: { realtime?: number | null; located?: boolean } = {},
): Departure => ({ line: "L3", destination: "Andorra la Vella", scheduled, realtime, located });

describe("board", () => {
  test("realtime wins and orders the board", () => {
    const board = parse(
      response(journey("130000"), journey("131300", { realtime: "125500", product: 1 })),
      now,
    );
    expect(board.map((it) => it.line)).toEqual(["L4", "L3"]);
    expect(time(board[0])).toBe(at(12, 55));
    expect(board[0].scheduled).toBe(at(13, 13));
  });

  test("drops cancelled, past and beyond the window", () => {
    const board = parse(
      response(
        journey("125500", { cancelled: true }),
        journey("124000"),
        journey("131300", { realtime: "124500" }),
        journey("145100"),
        journey("145000"),
      ),
      now,
    );
    expect(board.map(time)).toEqual([at(14, 50)]);
  });

  test("a day offset crosses midnight", () => {
    const board = parse(response(journey("01003000")), at(23, 50));
    expect(board.map(time)).toEqual([at(0, 30, 6)]);
  });

  test("clock times survive the daylight saving change", () => {
    const raw = response(journey("131300")).replace("20261105", "20261025");
    expect(parse(raw, at(13, 0, 25, 10)).map(time)).toEqual([at(13, 13, 25, 10)]);
  });

  test("a failed response throws", () => {
    expect(() => parse(JSON.stringify({ svcResL: [{ err: "H890" }] }), now)).toThrow("H890");
  });

  test("the request asks for local time at the stop", () => {
    const req = JSON.parse(request(now)).svcReqL[0].req;
    expect(req.date).toBe("20261105");
    expect(req.time).toBe("125000");
    expect(req.stbLoc.lid).toContain("L=948@");
  });

  test("source says how far to trust the time", () => {
    expect(source(departure(at(14, 0), { realtime: at(14, 1) }), now)).toBe("GPS");
    expect(source(departure(at(14, 0), { located: true }), now)).toBe("Localizado");
    expect(source(departure(at(13, 19)), now)).toBe("Sin confirmar");
    expect(source(departure(at(13, 20)), now)).toBeNull();
  });
});

describe("notice", () => {
  test("shows the next bus and the rest", () => {
    const shown = notice(
      [departure(at(13, 0), { realtime: at(12, 58) }), departure(at(14, 0))],
      false,
      now,
    );
    expect(shown.title).toBe("L3 · 12:58 · en 8 min");
    expect(shown.text).toBe("Andorra la Vella · GPS");
    expect(shown.lines).toEqual(["14:00 L3 · Andorra la Vella"]);
  });

  test("keeps the last board when offline", () => {
    const shown = notice([departure(at(13, 0))], true, now);
    expect(shown.offline).toBe(true);
    expect(shown.title).toBe("L3 · 13:00 · en 10 min");
  });

  test("explains an empty board", () => {
    expect(notice(undefined, false, now).text).toBe("Buscando buses…");
    expect(notice(undefined, true, now).text).toBe("Sin conexión");
    expect(notice([], true, now).text).toBe("Sin conexión");
    expect(notice([], false, now).text).toBe("Sin buses en las próximas 2 horas");
  });
});
