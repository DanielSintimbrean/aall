import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { Alert, AppState } from "react-native";
import { defaults, errorMessage, type InstalledApp, type Routine, type Snapshot } from "./model";
import { native, snapshot } from "./native";

type Store = {
  state: Snapshot;
  apps: InstalledApp[];
  icons: Record<string, string>;
  loadIcons(): void;
  act(fn: () => void): boolean;
};
type Draft = {
  routine: Routine;
  minutes: string;
};
type Drafts = {
  draft?: Draft;
  original?: Draft;
  begin(routine: Routine, minutes: number): void;
  update(change: Partial<Draft>): void;
};
const StoreContext = createContext<Store | null>(null);
const DraftContext = createContext<Drafts | null>(null);

export function useStore() {
  const store = useContext(StoreContext);
  if (!store) throw Error("useStore needs StoreProvider");
  return store;
}
export function useDraft() {
  const drafts = useContext(DraftContext);
  if (!drafts) throw Error("useDraft needs StoreProvider");
  return drafts;
}

export function StoreProvider({
  children,
  fallback,
}: {
  children: ReactNode;
  fallback: ReactNode;
}) {
  const [state, setState] = useState<Snapshot>();
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [icons, setIcons] = useState<Record<string, string>>({});
  const [original, setOriginal] = useState<Draft>();
  const [draft, setDraft] = useState<Draft>();
  const iconsRequested = useRef(false);
  const refresh = useCallback(() => {
    if (native) setState(snapshot());
  }, []);
  useEffect(() => {
    if (native) {
      const installed = native.installedApps();
      const packages = new Set(installed.map((app) => app.packageName));
      native.initialize(
        JSON.stringify({
          ...defaults,
          routines: defaults.routines.map((r) => ({
            ...r,
            apps: r.apps.filter((pkg) => packages.has(pkg)),
            colorApps: r.colorApps.filter((pkg) => packages.has(pkg)),
          })),
        }),
      );
      native.reconcile();
      // oxlint-disable-next-line react/set-state-in-effect -- Native data loads once after mount.
      setApps(installed);
      refresh();
    }
    const timer = setInterval(refresh, 1000);
    const sub = AppState.addEventListener("change", (appState) => {
      if (appState === "active") native?.reconcile();
      refresh();
    });
    return () => {
      clearInterval(timer);
      sub.remove();
    };
  }, [refresh]);
  const store = useMemo(
    () =>
      state && {
        state,
        apps,
        icons,
        loadIcons() {
          if (iconsRequested.current || !native) return;
          iconsRequested.current = true;
          native.appIcons().then(setIcons, () => {
            iconsRequested.current = false;
          });
        },
        act(fn: () => void) {
          try {
            fn();
            refresh();
            return true;
          } catch (error) {
            Alert.alert("That didn't work", errorMessage(error));
            return false;
          }
        },
      },
    [state, apps, icons, refresh],
  );
  const drafts = useMemo<Drafts>(
    () => ({
      draft,
      original,
      begin(routine, minutes) {
        const next = {
          // oxlint-disable-next-line typescript/no-unsafe-type-assertion -- JSON copy of a Routine.
          routine: JSON.parse(JSON.stringify(routine)) as Routine,
          minutes: String(minutes),
        };
        setOriginal(next);
        setDraft(next);
      },
      update(change) {
        setDraft((current) => current && { ...current, ...change });
      },
    }),
    [draft, original],
  );
  // oxlint-disable-next-line react/refs -- The ref is only read inside loadIcons, not during render.
  if (!native || !store) return fallback;
  return (
    <StoreContext.Provider value={store}>
      <DraftContext.Provider value={drafts}>{children}</DraftContext.Provider>
    </StoreContext.Provider>
  );
}
