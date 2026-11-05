import type { ComponentType } from "react";
import type { IconName } from "../ui";
import { Movent } from "./movent/Movent";

export const apps: {
  route: "Movent";
  title: string;
  subtitle: string;
  icon: IconName;
  screen: ComponentType;
}[] = [
  {
    route: "Movent",
    title: "Movent",
    subtitle: "Buses en Cascada de les Moles",
    icon: "bus-clock",
    screen: Movent,
  },
];
