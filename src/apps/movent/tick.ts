import { queryClient } from "../../query";
import { native } from "./native";
import { notice } from "./notice";
import { boardQuery } from "./query";

/** Runs every minute from the Movent service while the screen is on, with or without the UI. */
export async function tick() {
  let offline = false;
  try {
    await queryClient.fetchQuery({ ...boardQuery, staleTime: 0 });
  } catch {
    offline = true;
  }
  const shown = notice(queryClient.getQueryData(boardQuery.queryKey), offline, Date.now());
  native.show(shown.title, shown.text, shown.lines, shown.offline);
}
