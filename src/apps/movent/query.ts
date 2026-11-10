import { queryOptions } from "@tanstack/react-query";
import { fetchBoard } from "./board";

export const boardQuery = queryOptions({
  queryKey: ["movent", "board"],
  queryFn: () => fetchBoard(Date.now()),
  // The notification keeps showing the last board while offline, however long that lasts.
  gcTime: Infinity,
});
