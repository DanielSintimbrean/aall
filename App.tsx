import "./global.css";
import React, { useCallback, useEffect, useState } from "react";
import {
  Alert,
  AppState,
  Pressable,
  ScrollView,
  Switch,
  Text,
  TextInput,
  View,
} from "react-native";
import {
  SafeAreaProvider,
  SafeAreaView as NativeSafeAreaView,
} from "react-native-safe-area-context";
import { withUniwind } from "uniwind";
const SafeAreaView = withUniwind(NativeSafeAreaView);
import { StatusBar } from "expo-status-bar";
import {
  defaults,
  earliestDay,
  editors,
  statusLabels,
  validTime,
  type InstalledApp,
  type Routine,
  type Snapshot,
} from "./src/model";
import { native, saveConfig, saveToday, snapshot } from "./src/native";

function Button({ title, onPress }: { title: string; onPress(): void }) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      className="rounded-2xl bg-teal-900 p-4 my-1"
    >
      <Text className="text-teal-50 text-base">{title}</Text>
    </Pressable>
  );
}
function Field({
  value,
  onChangeText,
  label,
}: {
  value: string;
  onChangeText(v: string): void;
  label: string;
}) {
  return (
    <View className="my-2">
      <Text className="text-zinc-400 mb-1">{label}</Text>
      <TextInput
        accessibilityLabel={label}
        value={value}
        onChangeText={onChangeText}
        autoCapitalize="none"
        className="bg-zinc-800 text-white rounded-xl p-3 text-lg"
      />
    </View>
  );
}
function AppContent() {
  const [state, setState] = useState<Snapshot>();
  const [routine, setRoutine] = useState<Routine>();
  const [page, setPage] = useState("today");
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [query, setQuery] = useState("");
  const [list, setList] = useState<"apps" | "colorApps">("apps");
  const [date, setDate] = useState(earliestDay());
  const [expression, setExpression] = useState("");
  const [answer, setAnswer] = useState("");
  const [minutes, setMinutes] = useState("50");
  const refresh = useCallback(() => {
    if (native) setState(snapshot());
  }, []);
  const act = (fn: () => void) => {
    try {
      fn();
      refresh();
    } catch (e) {
      Alert.alert("aall", String(e));
    }
  };
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
      refresh();
      setApps(installed);
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
  if (!native || !state)
    return (
      <SafeAreaView className="flex-1 bg-zinc-950 p-6">
        <Text className="text-white text-2xl">Install the aall Android APK</Text>
        <Text className="text-zinc-400">Expo Go cannot run aall.</Text>
      </SafeAreaView>
    );
  const save = (today: boolean) =>
    act(() => {
      if (!routine) return;
      if (routine.windows.some((w) => !validTime(w.start) || !validTime(w.end)))
        throw Error("Use HH:mm times.");
      if (today) saveToday(routine);
      else
        saveConfig({
          ...state.config,
          routines: state.config.routines.map((r) => (r.id === routine.id ? routine : r)),
        });
      setRoutine(undefined);
      setPage("today");
    });
  const edit = (r: Routine) => {
    setRoutine(JSON.parse(JSON.stringify(r)));
    setMinutes(String(state.config.focusMinutes));
    setPage("edit");
  };
  const remaining = (until: number) => Math.max(0, Math.ceil((until - Date.now()) / 60000));
  const editor = routine && editors[routine.id];
  return (
    <SafeAreaView className="flex-1 bg-zinc-950">
      <StatusBar style="light" />
      <ScrollView contentContainerClassName="p-5 pb-16" keyboardShouldPersistTaps="handled">
        <View className="flex-row items-center justify-between mb-6">
          <Text className="text-white text-4xl font-bold">aall</Text>
          {page !== "today" && (
            <Button title="Back" onPress={() => setPage(page === "apps" ? "edit" : "today")} />
          )}
        </View>
        {page === "today" && (
          <>
            <Text className="text-zinc-400 text-lg">
              {new Date().toLocaleDateString(undefined, {
                weekday: "long",
                month: "long",
                day: "numeric",
              })}
            </Text>
            <Text className="text-white text-3xl mt-3 mb-2">
              {state.dayOff
                ? "Day off"
                : remaining(state.pauseUntil) > 0
                  ? `Paused · ${remaining(state.pauseUntil)} min`
                  : state.active.length
                    ? "Routines active"
                    : "Time is yours"}
            </Text>
            <Text className="text-zinc-400 mb-4">
              Next change {new Date(state.nextChange).toLocaleString()}
            </Text>
            {state.errors.map((error) => (
              <Text key={error} className="text-amber-300 mb-2">
                {error}
              </Text>
            ))}
            {state.config.routines.map((r) => (
              <Pressable
                key={r.id}
                onPress={() => edit(r)}
                className="bg-zinc-900 rounded-2xl p-5 my-2"
              >
                <View className="flex-row justify-between">
                  <Text className="text-white text-xl">{r.title}</Text>
                  <Text className="text-teal-300">{statusLabels[state.statuses[r.id]]}</Text>
                </View>
                <Text className="text-zinc-400 mt-2">
                  {editors[r.id].session
                    ? `${state.config.focusMinutes} minutes · ${remaining(state.focusUntil)} remaining`
                    : r.windows.map((w) => `${w.start}–${w.end}`).join(" · ")}
                  {editors[r.id].cardNote}
                </Text>
              </Pressable>
            ))}
            <Button title="Start focus" onPress={() => act(() => native!.startFocus())} />
            <Button
              title="Pause for 15 minutes"
              onPress={() =>
                act(() => {
                  setExpression(native!.challenge());
                  setAnswer("");
                  setPage("bypass");
                })
              }
            />
            <Button title="Days off" onPress={() => setPage("days")} />
            <Button title="Phone setup" onPress={() => setPage("setup")} />
          </>
        )}
        {page === "edit" && routine && editor && (
          <>
            <Text className="text-white text-3xl mb-4">{routine.title}</Text>
            <View className="flex-row justify-between items-center">
              <Text className="text-zinc-300 text-lg">Enabled</Text>
              <Switch
                value={routine.enabled}
                onValueChange={(enabled) => setRoutine({ ...routine, enabled })}
              />
            </View>
            {editor.hint && <Text className="text-zinc-400 mt-3">{editor.hint}</Text>}
            {editor.session && (
              <>
                <Field
                  label="Focus duration in minutes"
                  value={minutes}
                  onChangeText={setMinutes}
                />
                <Button
                  title="Save duration"
                  onPress={() =>
                    act(() => {
                      const n = Number(minutes);
                      if (!Number.isInteger(n) || n < 1 || n > 1440)
                        throw Error("Choose 1 to 1440 minutes.");
                      saveConfig({ ...state.config, focusMinutes: n });
                    })
                  }
                />
              </>
            )}
            {routine.windows.map((w, i) => (
              <View key={i} className="bg-zinc-900 p-4 rounded-xl my-3">
                <Text className="text-teal-300">
                  {editor.windowLabel} {i + 1}
                </Text>
                {(["start", "end"] as const).map((key) => (
                  <Field
                    key={key}
                    label={`${key} · HH:mm`}
                    value={w[key]}
                    onChangeText={(v) =>
                      setRoutine({
                        ...routine,
                        windows: routine.windows.map((item, j) =>
                          j === i ? { ...item, [key]: v } : item,
                        ),
                      })
                    }
                  />
                ))}
                <View className="flex-row flex-wrap">
                  {["M", "T", "W", "T", "F", "S", "S"].map((day, j) => (
                    <Pressable
                      key={j}
                      accessibilityLabel={`Weekday ${j + 1}`}
                      onPress={() =>
                        setRoutine({
                          ...routine,
                          windows: routine.windows.map((item, k) =>
                            k === i
                              ? {
                                  ...item,
                                  days: item.days.includes(j + 1)
                                    ? item.days.filter((d) => d !== j + 1)
                                    : [...item.days, j + 1],
                                }
                              : item,
                          ),
                        })
                      }
                      className={`p-3 rounded-xl mr-1 my-1 ${w.days.includes(j + 1) ? "bg-teal-800" : "bg-zinc-800"}`}
                    >
                      <Text className="text-white">{day}</Text>
                    </Pressable>
                  ))}
                </View>
                {editor.windowCountEditable && (
                  <Button
                    title="Remove window"
                    onPress={() =>
                      setRoutine({ ...routine, windows: routine.windows.filter((_, j) => j !== i) })
                    }
                  />
                )}
              </View>
            ))}
            {editor.windowCountEditable && (
              <Button
                title="Add hours"
                onPress={() =>
                  setRoutine({
                    ...routine,
                    windows: [
                      ...routine.windows,
                      { start: "09:00", end: "17:00", days: [1, 2, 3, 4, 5] },
                    ],
                  })
                }
              />
            )}
            {editor.chargingStart && (
              <Field
                label="Charging may start bedtime after · HH:mm, at least 20:00"
                value={routine.earlyStart}
                onChangeText={(earlyStart) => setRoutine({ ...routine, earlyStart })}
              />
            )}
            {editor.appList && (
              <Button
                title={`${editor.appList} apps · ${routine.apps.length}`}
                onPress={() => {
                  setList("apps");
                  setQuery("");
                  setPage("apps");
                }}
              />
            )}
            {editor.colorExceptions && (
              <Button
                title={`Keep color in apps · ${routine.colorApps.length}`}
                onPress={() => {
                  setList("colorApps");
                  setQuery("");
                  setPage("apps");
                }}
              />
            )}
            <Button title="Save weekly settings" onPress={() => save(false)} />
            {!editor.session && <Button title="Apply only today" onPress={() => save(true)} />}
          </>
        )}
        {page === "apps" && routine && (
          <>
            <Text className="text-white text-2xl">Choose apps</Text>
            <Field label="Search installed apps" value={query} onChangeText={setQuery} />
            {apps
              .filter((a) =>
                `${a.label} ${a.packageName}`.toLowerCase().includes(query.toLowerCase()),
              )
              .map((a) => (
                <Pressable
                  key={a.packageName}
                  onPress={() =>
                    setRoutine({
                      ...routine,
                      [list]: routine[list].includes(a.packageName)
                        ? routine[list].filter((p) => p !== a.packageName)
                        : [...routine[list], a.packageName],
                    })
                  }
                  className="py-4 border-b border-zinc-800"
                >
                  <Text className="text-white text-lg">
                    {routine[list].includes(a.packageName) ? "✓  " : ""}
                    {a.label}
                  </Text>
                  <Text className="text-zinc-500">{a.packageName}</Text>
                </Pressable>
              ))}
          </>
        )}
        {page === "bypass" && (
          <>
            <Text className="text-white text-2xl">Pause all restrictions</Text>
            <Text className="text-teal-300 text-3xl my-6">{expression} = ?</Text>
            <Field label="Answer" value={answer} onChangeText={setAnswer} />
            <Button
              title="Check answer"
              onPress={() =>
                act(() => {
                  if (native!.solve(answer)) setPage("today");
                  else Alert.alert("Try again");
                })
              }
            />
          </>
        )}
        {page === "days" && (
          <>
            <Text className="text-white text-3xl">Days off</Text>
            <Text className="text-zinc-400 my-4">
              Restrictions pause from midnight to midnight. Book at least 72 hours ahead. Earliest
              date: {earliestDay()}.
            </Text>
            <Field label="Date · YYYY-MM-DD" value={date} onChangeText={setDate} />
            <Button title="Book day off" onPress={() => act(() => native!.bookDay(date))} />
            {state.daysOff.map((d) => (
              <Button
                key={d}
                title={`${d} · cancel booking`}
                onPress={() => act(() => native!.cancelDay(d))}
              />
            ))}
          </>
        )}
        {page === "setup" && (
          <>
            <Text className="text-white text-3xl mb-4">Phone setup</Text>
            {(["accessibility", "dnd", "alarms", "grayscale"] as const).map((kind) => (
              <Button
                key={kind}
                title={`${kind} · ${state.permissions[kind] ? "Granted" : "Needs setup"}`}
                onPress={() => act(() => native!.openSettings(kind))}
              />
            ))}
            <Text selectable className="text-zinc-300 my-4">
              For grayscale, connect ADB once and run:{"\n\n"}adb shell pm grant dev.danields.aall
              android.permission.WRITE_SECURE_SETTINGS
            </Text>
            <Text className="text-zinc-400">
              Sideloaded apps may need Allow restricted settings in app info before accessibility
              can be enabled.
            </Text>
            <Button
              title="Test grayscale for 10 seconds"
              onPress={() => act(() => native!.testGrayscale(true))}
            />
            <Button
              title="Restore color now"
              onPress={() => act(() => native!.testGrayscale(false))}
            />
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}
export default function App() {
  return (
    <SafeAreaProvider>
      <AppContent />
    </SafeAreaProvider>
  );
}
