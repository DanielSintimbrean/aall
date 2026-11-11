import React from "react";
import { ScrollView, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { apps } from "../apps";
import {
  clockLabel,
  dateLabel,
  daysLabel,
  editors,
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
  nightLight: "weather-sunset",
};

type Hero = { title: string; detail: string; on: boolean; running?: Routine[] };

function hero(state: Snapshot, routines: Routine[], now: number): Hero {
  if (state.dayOff)
    return {
      title: "Día libre",
      detail: "Todas las restricciones están suspendidas hasta medianoche.",
      on: false,
    };
  const paused = minutesLeft(state.pauseUntil, now);
  if (paused)
    return {
      title: "En pausa",
      detail: `Las restricciones se reanudan en ${paused} min.`,
      on: false,
    };
  const running = routines
    .filter((r) => state.active.includes(r.id))
    .sort((a, b) => (state.ends[a.id] ?? Infinity) - (state.ends[b.id] ?? Infinity));
  if (running.length)
    return {
      title: running.length === 1 ? running[0].title : `${running.length} rutinas activas`,
      detail: "",
      on: true,
      running,
    };
  if (!state.config.routines.some((r) => r.enabled))
    return {
      title: "Todas las rutinas desactivadas",
      detail: "Activa una rutina para empezar.",
      on: false,
    };
  return { title: "El tiempo es tuyo", detail: "Ninguna rutina está activa ahora.", on: false };
}

function endLabel(id: Routine["id"], state: Snapshot, now: number) {
  const end = state.ends[id];
  return end ? `hasta ${clockLabel(end, now)}` : "";
}

function routineDetail(routine: Routine, state: Snapshot, now: number) {
  const editor = editors[routine.id];
  const turningOff = state.turningOff[routine.id];
  const off = turningOff ? ` · se desactiva el ${clockLabel(turningOff, now)}` : "";
  if (editor.session)
    return `${state.config.focusMinutes} min · ${routine.apps.length} aplicaciones bloqueadas${off}`;
  const hours = `${editor.cardPrefix ?? ""}${windowsLabel(routine.windows)}`;
  const days = routine.windows.length === 1 ? ` · ${daysLabel(routine.windows[0].days)}` : "";
  const ramp = editor.ramp ? ` · ${routine.rampMinutes} min hasta ${routine.finalIntensity} %` : "";
  return `${hours}${days}${ramp}${state.overrides[routine.id] ? " · solo hoy" : ""}${off}`;
}

export function Today({ navigation }: ScreenProps<"Today">) {
  const { state, act } = useStore();
  const { begin } = useDraft();
  const insets = useSafeAreaInsets();
  // oxlint-disable-next-line react/purity -- The snapshot poll re-renders with the current time.
  const now = Date.now();
  const routines = state.config.routines.map((weekly) => state.overrides[weekly.id] ?? weekly);
  const summary = hero(state, routines, now);
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
        {summary.running && (
          <View className="gap-2">
            {summary.running.map((routine) => (
              <View key={routine.id} className="flex-row items-center gap-3">
                <Icon name={routineIcons[routine.id]} size={20} color={colors.accent} />
                <Text className="flex-1 text-base text-ink">{routine.title}</Text>
                <Text className="text-base text-muted">{endLabel(routine.id, state, now)}</Text>
              </View>
            ))}
          </View>
        )}
        {!focusRunning &&
          focusRoutine &&
          (focusRoutine.enabled ? (
            <Button
              title={`Iniciar concentración · ${state.config.focusMinutes} min`}
              icon="play"
              onPress={() => act(() => native!.startFocus())}
            />
          ) : (
            <Button
              title="Configurar concentración"
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

      <Section title="Rutinas">
        <Card>
          {routines.map((routine, index) => (
            <View key={routine.id}>
              {index > 0 && <Divider />}
              <Row
                icon={routineIcons[routine.id]}
                title={routine.title}
                subtitle={routineDetail(routine, state, now)}
                trailing={<StatusPill status={state.statuses[routine.id]} />}
                onPress={() => open(routine)}
              />
            </View>
          ))}
        </Card>
      </Section>

      <Section title="Aplicaciones">
        <Card>
          {apps.map((app, index) => (
            <View key={app.route}>
              {index > 0 && <Divider />}
              <Row
                icon={app.icon}
                title={app.title}
                subtitle={app.subtitle}
                onPress={() => navigation.navigate(app.route)}
              />
            </View>
          ))}
        </Card>
      </Section>

      <Section title="Más">
        <Card>
          <Row
            icon="calendar-heart"
            title="Días libres"
            subtitle={upcoming[0] ? `Próximo: ${dateLabel(upcoming[0])}` : "Sin reservas"}
            onPress={() => navigation.navigate("DaysOff")}
          />
          <Divider />
          {!paused && (
            <>
              <Row
                icon="pause-circle-outline"
                title="Pausar durante 15 minutos"
                subtitle="Resuelve una suma para suspender todas las restricciones"
                onPress={() => navigation.navigate("Bypass")}
              />
              <Divider />
            </>
          )}
          <Row
            icon="cellphone-cog"
            title="Configuración del teléfono"
            subtitle={
              missing
                ? `${missing} permiso${missing > 1 ? "s" : ""} sin conceder`
                : "Todos los permisos concedidos"
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
