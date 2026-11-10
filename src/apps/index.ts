import type { ComponentType } from "react";
import type { IconName } from "../ui";
import { Movent } from "./movent/Movent";
import { tick } from "./movent/tick";

export const apps: {
  route: "Movent";
  title: string;
  subtitle: string;
  icon: IconName;
  screen: ComponentType;
  /** A headless JS task the app's native service starts by name. */
  task?: { name: string; run: () => Promise<void> };
}[] = [
  {
    route: "Movent",
    title: "Movent",
    subtitle: "Buses en Cascada de les Moles",
    icon: "bus-clock",
    screen: Movent,
    task: { name: "MoventTick", run: tick },
  },
];
