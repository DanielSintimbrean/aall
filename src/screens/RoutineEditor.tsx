import React, { useEffect, useState } from "react";
import { usePreventRemove } from "@react-navigation/native";
import { Alert, Pressable, Switch, Text, ToastAndroid, View } from "react-native";
import {
  earliestChargingStart,
  editors,
  isOvernight,
  parseMinutes,
  type InstalledApp,
  type Routine,
  type Window,
} from "../model";
import type { ScreenProps } from "../navigation";
import { saveConfig, saveToday, saveWeekly } from "../native";
import { useDraft, useStore } from "../store";
import { colors } from "../theme";
import {
  BottomBar,
  Button,
  Card,
  Chip,
  DayPicker,
  Divider,
  FieldError,
  Icon,
  KeyboardScreen,
  Notice,
  Page,
  Row,
  Section,
  Stepper,
  TimeField,
} from "../ui";

const focusPresets = [25, 50, 90];

function appNames(packages: string[], apps: InstalledApp[]) {
  if (!packages.length) return "None";
  const names = packages.map(
    (pkg) => apps.find((app) => app.packageName === pkg)?.label ?? pkg.split(".").pop(),
  );
  return names.length > 2
    ? `${names.slice(0, 2).join(", ")} +${names.length - 2}`
    : names.join(", ");
}

function HoursCard({
  routine,
  window,
  index,
  onChange,
  onRemove,
}: {
  routine: Routine;
  window: Window;
  index: number;
  onChange(window: Window): void;
  onRemove?(): void;
}) {
  const noun = editors[routine.id].windowLabel;
  return (
    <Card className="gap-4 p-4">
      <View className="min-h-8 flex-row items-center justify-between">
        <Text className="text-base font-semibold text-ink">
          {routine.windows.length > 1 ? `${noun} ${index + 1}` : noun}
        </Text>
        {onRemove && (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`Remove ${noun.toLowerCase()} ${index + 1}`}
            hitSlop={4}
            onPress={onRemove}
            className="h-10 w-10 items-center justify-center"
          >
            <Icon name="trash-can-outline" size={20} color={colors.danger} />
          </Pressable>
        )}
      </View>
      <View className="flex-row gap-2">
        <TimeField
          label="Start"
          value={window.start}
          onChange={(start) => onChange({ ...window, start })}
        />
        <TimeField
          label={isOvernight(window) ? "End · next day" : "End"}
          value={window.end}
          onChange={(end) => onChange({ ...window, end })}
        />
      </View>
      <DayPicker days={window.days} onChange={(days) => onChange({ ...window, days })} />
      {!window.days.length && <FieldError>Pick at least one day.</FieldError>}
    </Card>
  );
}

export function RoutineEditor({ navigation }: ScreenProps<"Routine">) {
  const { state, apps, act } = useStore();
  const { draft, original, update } = useDraft();
  const [saved, setSaved] = useState(false);
  const dirty = JSON.stringify(draft) !== JSON.stringify(original);
  usePreventRemove(dirty && !saved, ({ data }) => {
    Alert.alert("Discard changes?", "Your changes to this routine haven't been saved.", [
      { text: "Keep editing", style: "cancel" },
      { text: "Discard", style: "destructive", onPress: () => navigation.dispatch(data.action) },
    ]);
  });
  useEffect(() => {
    if (saved) navigation.popTo("Today");
  }, [saved, navigation]);
  if (!draft) return null;
  const { routine, minutes } = draft;
  const setRoutine = (change: Partial<Routine>) => update({ routine: { ...routine, ...change } });
  const setWindow = (index: number, window: Window) =>
    setRoutine({ windows: routine.windows.map((w, i) => (i === index ? window : w)) });
  const editor = editors[routine.id];
  const parsedMinutes = parseMinutes(minutes);
  const earlyInvalid =
    editors[routine.id].chargingStart && routine.earlyStart < earliestChargingStart;
  const invalid =
    routine.windows.some((w) => !w.days.length) ||
    earlyInvalid ||
    (editor.session && parsedMinutes === null);
  const overridden = !!state.overrides[routine.id];
  const save = (today: boolean) => {
    const done = act(() => {
      if (today) return saveToday(routine);
      if (editor.session && parsedMinutes !== state.config.focusMinutes)
        saveConfig({ ...state.config, focusMinutes: parsedMinutes! });
      saveWeekly(routine);
    });
    if (!done) return;
    ToastAndroid.show(today ? "Applied for today" : "Saved", ToastAndroid.SHORT);
    setSaved(true);
  };
  return (
    <KeyboardScreen>
      <Page bare>
        <Card>
          <Row
            title="Enabled"
            subtitle={editor.hint}
            chevron={false}
            trailing={
              <Switch
                accessibilityLabel="Enabled"
                value={routine.enabled}
                onValueChange={(enabled) => setRoutine({ enabled })}
                trackColor={{ false: colors.raised, true: colors.accent }}
                thumbColor={routine.enabled ? colors.onAccent : colors.muted}
              />
            }
          />
        </Card>

        {overridden && (
          <Notice icon="calendar-today">
            Showing today's hours. Save weekly to keep them every week and end the only-today
            change.
          </Notice>
        )}

        {editor.session && (
          <Section title="Duration">
            <Card className="gap-3 p-4">
              <View accessibilityRole="radiogroup" className="flex-row gap-2">
                {focusPresets.map((preset) => (
                  <Chip
                    key={preset}
                    label={`${preset} min`}
                    selected={parsedMinutes === preset}
                    onPress={() => update({ minutes: String(preset) })}
                  />
                ))}
              </View>
              <Stepper
                label="Focus duration in minutes"
                value={minutes}
                error={parsedMinutes === null}
                onChange={(value) => update({ minutes: value.replace(/\D/g, "") })}
              />
              {parsedMinutes === null && <FieldError>Choose 1 to 1440 minutes.</FieldError>}
            </Card>
          </Section>
        )}

        {!editor.session && (
          <Section title="Schedule">
            {routine.windows.map((window, index) => (
              <HoursCard
                key={index}
                routine={routine}
                window={window}
                index={index}
                onChange={(next) => setWindow(index, next)}
                onRemove={
                  editor.windowCountEditable
                    ? () => setRoutine({ windows: routine.windows.filter((_, i) => i !== index) })
                    : undefined
                }
              />
            ))}
            {editor.windowCountEditable && !routine.windows.length && (
              <Notice tone="warn">{editor.noHoursWarning}</Notice>
            )}
            {editor.windowCountEditable && (
              <Button
                title="Add hours"
                variant="outline"
                icon="plus"
                onPress={() =>
                  setRoutine({
                    windows: [
                      ...routine.windows,
                      { start: "09:00", end: "17:00", days: [1, 2, 3, 4, 5] },
                    ],
                  })
                }
              />
            )}
          </Section>
        )}

        {editor.chargingStart && (
          <Section title="Early start when charging">
            <Card className="gap-3 p-4">
              <TimeField
                label="Plugging in starts bedtime after"
                value={routine.earlyStart}
                onChange={(earlyStart) => setRoutine({ earlyStart })}
              />
              {earlyInvalid ? (
                <FieldError>Choose {earliestChargingStart} or later.</FieldError>
              ) : (
                <Text className="px-1 text-sm text-muted">
                  {earliestChargingStart} or later. Unplugging doesn't cancel bedtime.
                </Text>
              )}
            </Card>
          </Section>
        )}

        <Section title="Apps">
          <Card>
            {editor.appList && (
              <Row
                icon={editor.appList === "Allowed" ? "check-circle-outline" : "cancel"}
                title={`${editor.appList} apps`}
                subtitle={appNames(routine.apps, apps)}
                onPress={() => navigation.navigate("AppPicker", { list: "apps" })}
              />
            )}
            {editor.appList && editor.colorExceptions && <Divider />}
            {editor.colorExceptions && (
              <Row
                icon="palette-outline"
                title="Keep color in"
                subtitle={appNames(routine.colorApps, apps)}
                onPress={() => navigation.navigate("AppPicker", { list: "colorApps" })}
              />
            )}
          </Card>
        </Section>
      </Page>
      <BottomBar>
        <View className="flex-row gap-2">
          {!editor.session && (
            <View className="flex-1">
              <Button
                title="Only today"
                variant="secondary"
                disabled={!dirty || invalid}
                onPress={() => save(true)}
              />
            </View>
          )}
          <View className="flex-1">
            <Button
              title={editor.session ? "Save" : "Save weekly"}
              disabled={(!dirty && !overridden) || invalid}
              onPress={() => save(false)}
            />
          </View>
        </View>
      </BottomBar>
    </KeyboardScreen>
  );
}
