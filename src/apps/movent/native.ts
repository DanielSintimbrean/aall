import { requireOptionalNativeModule } from "expo-modules-core";

type Native = {
  isRunning(): boolean;
  setRunning(running: boolean): void;
  notificationsEnabled(): boolean;
  show(title: string, text: string, lines: string[], offline: boolean): void;
};

// Optional so importing this file does not break the install message in Expo Go.
export const native = requireOptionalNativeModule<Native>("Movent")!;
