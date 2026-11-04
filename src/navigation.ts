import type { NativeStackScreenProps } from "@react-navigation/native-stack";

export type Routes = {
  Today: undefined;
  Routine: { title: string };
  AppPicker: { list: "apps" | "colorApps" };
  Bypass: undefined;
  DaysOff: undefined;
  Setup: undefined;
};
export type ScreenProps<Name extends keyof Routes> = NativeStackScreenProps<Routes, Name>;
