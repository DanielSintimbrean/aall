import React from "react";
import { ScrollView, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import {
  clockLabel,
  dateLabel,
  daysLabel,
  editors,
  focusMinutesLeft,
  localDate,
  minutesLeft,
  windowsLabel,
  type Routine,
  type Snapshot,
} from "../model";
import type { ScreenProps } from "../navigation";
import { native } from "../native";
import { useDraft, useStore } from "../store";
import { colors } from "../theme";
import {
  Button,
  Card,
  Divider,
  Icon,
  Notice,
  Row,
  Section,
  StatusPill,
  type IconName,
} from "../ui";

const routineIcons: Record<Routine["id"], IconName> = {
  access: "application-outline",
  focus: "target",
  bedtime: "weather-night",
  grayscale: "palette-outline",
};

function hero(state: Snapshot, now: number) {
  const titles = state.config.routines
    .filter((r) => state.active.includes(r.id))
    .map((r) => r.title);
  const next = new Date(state.nextChange);
  const meaningfulNext = next.getHours() + next.getMinutes() > 0;
  const nextLine = meaningfulNext ? `Next change at ${clockLabel(state.nextChange, now)}` : "";
  if (state.dayOff)
    return { title: "Day off", detail: "All restrictions are off until midnight.", on: false };
  const paused = minutesLeft(state.pauseUntil, now);
  if (paused)
    return { title: "Paused", detail: `Restrictions resume in ${paused} min.`, on: false };
  const focus = focusMinutesLeft(state, now);
  if (focus) return { title: `Focus · ${focus} min left`, detail: nextLine, on: true };
  if (titles.length) return { title: titles.join(", "), detail: nextLine, on: true };
  if (!state.config.routines.some((r) => r.enabled))
    return { title: "All routines off", detail: "Turn one on below to get started.", on: false };
  return { title: "Time is yours", detail: nextLine, on: false };
}

function routineDetail(routine: Routine, state: Snapshot) {
  const editor = editors[routine.id];
  if (editor.session)
    return `${state.config.focusMinutes} min · ${routine.apps.length} apps blocked`;
  const hours = `${editor.cardPrefix ?? ""}${windowsLabel(routine.windows)}`;
  const days = routine.windows.length === 1 ? ` · ${daysLabel(routine.windows[0].days)}` : "";
  return `${hours}${days}${state.overrides[routine.id] ? " · only today" : ""}`;
}

export function Today({ navigation }: ScreenProps<"Today">) {
  const { state, act } = useStore();
  const { begin } = useDraft();
  const insets = useSafeAreaInsets();
  // oxlint-disable-next-line react/purity -- The snapshot poll re-renders with the current time.
  const now = Date.now();
  const summary = hero(state, now);
  const routines = state.config.routines.map((weekly) => state.overrides[weekly.id] ?? weekly);
  const focusRoutine = routines.find((r) => editors[r.id].session);
  const focusRunning = !!focusRoutine?.enabled && minutesLeft(state.focusUntil, now) > 0;
  const paused = minutesLeft(state.pauseUntil, now) > 0;
  const open = (routine: Routine) => {
    begin(routine, state.config.focusMinutes);
    navigation.navigate("Routine", { title: routine.title });
  };
  const today = localDate(new Date(now));
  const upcoming = state.daysOff.filter((day) => day >= today).sort();
  const missing = Object.values(state.permissions).filter((granted) => !granted).length;
  return (
    <ScrollView
      className="flex-1 bg-canvas"
      contentContainerClassName="gap-6 px-4"
      contentContainerStyle={{ paddingTop: insets.top + 20, paddingBottom: insets.bottom + 24 }}
    >
      <View className="px-1">
        <Text className="text-sm font-semibold text-muted">{dateLabel(today)}</Text>
      </View>

      <Card className={`gap-4 p-5 ${summary.on ? "border-2 border-accent" : ""}`}>
        <View className="gap-1">
          <Text className="text-3xl font-bold text-ink">{summary.title}</Text>
          {!!summary.detail && <Text className="text-base text-muted">{summary.detail}</Text>}
        </View>
        {!focusRunning &&
          focusRoutine &&
          (focusRoutine.enabled ? (
            <Button
              title={`Start focus · ${state.config.focusMinutes} min`}
              icon="play"
              onPress={() => act(() => native!.startFocus())}
            />
          ) : (
            <Button
              title="Set up focus"
              variant="secondary"
              icon="target"
              onPress={() => open(focusRoutine)}
            />
          ))}
      </Card>

      {state.errors.map((error) => (
        <Notice key={error} tone="warn">
          {error}
        </Notice>
      ))}

      <Section title="Routines">
        <Card>
          {routines.map((routine, index) => (
            <View key={routine.id}>
              {index > 0 && <Divider />}
              <Row
                icon={routineIcons[routine.id]}
                title={routine.title}
                subtitle={routineDetail(routine, state)}
                trailing={<StatusPill status={state.statuses[routine.id]} />}
                onPress={() => open(routine)}
              />
            </View>
          ))}
        </Card>
      </Section>

      <Section title="More">
        <Card>
          <Row
            icon="calendar-heart"
            title="Days off"
            subtitle={upcoming[0] ? `Next: ${dateLabel(upcoming[0])}` : "None booked"}
            onPress={() => navigation.navigate("DaysOff")}
          />
          <Divider />
          {!paused && (
            <>
              <Row
                icon="pause-circle-outline"
                title="Pause for 15 minutes"
                subtitle="Solve a sum to lift all restrictions"
                onPress={() => navigation.navigate("Bypass")}
              />
              <Divider />
            </>
          )}
          <Row
            icon="cellphone-cog"
            title="Phone setup"
            subtitle={
              missing
                ? `${missing} permission${missing > 1 ? "s" : ""} missing`
                : "All permissions granted"
            }
            trailing={
              missing ? <Icon name="alert-circle-outline" color={colors.warn} /> : undefined
            }
            onPress={() => navigation.navigate("Setup")}
          />
        </Card>
      </Section>
    </ScrollView>
  );
}
