import { requireOptionalNativeModule } from "expo-modules-core";
import type { InstalledApp, Snapshot, Routine } from "./model";
type Native = {
  initialize(config: string): void;
  getSnapshot(): string;
  reconcile(): void;
  setFocusMinutes(minutes: number): void;
  saveWeekly(routine: string): void;
  installedApps(): InstalledApp[];
  appIcons(): Promise<Record<string, string>>;
  requestTurnOff(id: string): void;
  cancelTurnOff(id: string): void;
  startFocus(): void;
  challenge(): string;
  solve(answer: string): boolean;
  bookDay(date: string): void;
  cancelDay(date: string): void;
  overrideToday(id: string, routine: string): void;
  openSettings(kind: string): void;
  testGrayscale(enabled: boolean): void;
};
export const native = requireOptionalNativeModule<Native>("AallAutomation");
export function snapshot(): Snapshot {
  return JSON.parse(native!.getSnapshot());
}
export function saveWeekly(routine: Routine) {
  native!.saveWeekly(JSON.stringify(routine));
}
export function saveToday(routine: Routine) {
  native!.overrideToday(routine.id, JSON.stringify(routine));
}
