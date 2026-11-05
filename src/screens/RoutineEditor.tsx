import React, { useEffect, useState } from "react";
import { usePreventRemove } from "@react-navigation/native";
import { Alert, Pressable, Switch, Text, ToastAndroid, View } from "react-native";
import { checkDraft, focusMinutesToSave, isDirty, type DraftEdit } from "../draft";
import {
  clockLabel,
  earliestChargingStart,
  editors,
  isOvernight,
  type InstalledApp,
  type Routine,
  type Window,
} from "../model";
import type { ScreenProps } from "../navigation";
import { native, saveToday, saveWeekly } from "../native";
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
  if (!packages.length) return "Ninguna";
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
  dispatch,
  removable,
}: {
  routine: Routine;
  window: Window;
  index: number;
  dispatch(edit: DraftEdit): void;
  removable: boolean;
}) {
  const noun = editors[routine.id].windowLabel;
  return (
    <Card className="gap-4 p-4">
      <View className="min-h-8 flex-row items-center justify-between">
        <Text className="text-base font-semibold text-ink">
          {routine.windows.length > 1 ? `${noun} ${index + 1}` : noun}
        </Text>
        {removable && (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`Eliminar ${noun.toLowerCase()} ${index + 1}`}
            hitSlop={4}
            onPress={() => dispatch({ type: "removeWindow", index })}
            className="h-10 w-10 items-center justify-center"
          >
            <Icon name="trash-can-outline" size={20} color={colors.danger} />
          </Pressable>
        )}
      </View>
      <View className="flex-row gap-2">
        <TimeField
          label="Inicio"
          value={window.start}
          onChange={(start) => dispatch({ type: "setWindowTime", index, time: { start } })}
        />
        <TimeField
          label={isOvernight(window) ? "Fin · día siguiente" : "Fin"}
          value={window.end}
          onChange={(end) => dispatch({ type: "setWindowTime", index, time: { end } })}
        />
      </View>
      <DayPicker
        days={window.days}
        onToggle={(day) => dispatch({ type: "toggleWindowDay", index, day })}
      />
      {!window.days.length && <FieldError>Selecciona al menos un día.</FieldError>}
    </Card>
  );
}

export function RoutineEditor({ navigation }: ScreenProps<"Routine">) {
  const { state, apps, act } = useStore();
  const { draft, original, dispatch } = useDraft();
  const [saved, setSaved] = useState(false);
  usePreventRemove(isDirty(draft, original) && !saved, ({ data }) => {
    Alert.alert("¿Descartar los cambios?", "Los cambios de esta rutina no se han guardado.", [
      { text: "Seguir editando", style: "cancel" },
      { text: "Descartar", style: "destructive", onPress: () => navigation.dispatch(data.action) },
    ]);
  });
  useEffect(() => {
    if (saved) navigation.popTo("Today");
  }, [saved, navigation]);
  if (!draft) return null;
  const { routine, minutes } = draft;
  const editor = editors[routine.id];
  const overridden = !!state.overrides[routine.id];
  const weeklyOn = state.config.routines.some((r) => r.id === routine.id && r.enabled);
  const turningOff = state.turningOff[routine.id];
  const setEnabled = (enabled: boolean) => {
    if (enabled || !weeklyOn) return dispatch({ type: "setEnabled", enabled });
    Alert.alert(
      `¿Desactivar ${routine.title}?`,
      "La rutina seguirá su horario durante 72 horas y después se desactivará. Puedes cancelar la solicitud hasta entonces. El plazo empieza ahora y no guarda los demás cambios de esta pantalla.",
      [
        { text: "Mantener activada", style: "cancel" },
        {
          text: "Iniciar plazo de 72 horas",
          style: "destructive",
          onPress: () => act(() => native!.requestTurnOff(routine.id)),
        },
      ],
    );
  };
  const {
    minutes: parsedMinutes,
    earlyStartInvalid,
    canSaveToday,
    canSaveWeekly,
  } = checkDraft(draft, original, overridden);
  const save = (today: boolean) => {
    const done = act(() => {
      if (today) return saveToday(routine);
      const focusMinutes = focusMinutesToSave(draft, state.config.focusMinutes);
      if (focusMinutes !== null) native!.setFocusMinutes(focusMinutes);
      saveWeekly(routine);
    });
    if (!done) return;
    ToastAndroid.show(today ? "Aplicado para hoy" : "Guardado", ToastAndroid.SHORT);
    setSaved(true);
  };
  return (
    <KeyboardScreen>
      <Page bare>
        <Card>
          <Row
            title="Activada"
            subtitle={editor.hint}
            chevron={false}
            trailing={
              <Switch
                accessibilityLabel="Activada"
                value={routine.enabled}
                disabled={!!turningOff}
                onValueChange={setEnabled}
                trackColor={{ false: colors.raised, true: colors.accent }}
                thumbColor={routine.enabled ? colors.onAccent : colors.muted}
              />
            }
          />
        </Card>

        {!!turningOff && (
          <Card className="gap-3 p-4">
            <Text className="text-base text-ink">Se desactivará el {clockLabel(turningOff)}.</Text>
            <Button
              title="Mantener activada"
              variant="secondary"
              onPress={() => act(() => native!.cancelTurnOff(routine.id))}
            />
          </Card>
        )}

        {overridden && (
          <Notice icon="calendar-today">
            Se muestra el horario de hoy. Guarda el horario semanal para repetirlo cada semana y
            eliminar el cambio de solo hoy.
          </Notice>
        )}

        {editor.session && (
          <Section title="Duración">
            <Card className="gap-3 p-4">
              <View accessibilityRole="radiogroup" className="flex-row gap-2">
                {focusPresets.map((preset) => (
                  <Chip
                    key={preset}
                    label={`${preset} min`}
                    selected={parsedMinutes === preset}
                    onPress={() => dispatch({ type: "setMinutes", value: String(preset) })}
                  />
                ))}
              </View>
              <Stepper
                label="Duración de la concentración en minutos"
                value={minutes}
                error={parsedMinutes === null}
                onChange={(value) => dispatch({ type: "setMinutes", value })}
                onStep={(by) => dispatch({ type: "stepMinutes", by })}
              />
              {parsedMinutes === null && <FieldError>Elige entre 1 y 1440 minutos.</FieldError>}
            </Card>
          </Section>
        )}

        {!editor.session && (
          <Section title="Horario">
            {routine.windows.map((window, index) => (
              <HoursCard
                key={index}
                routine={routine}
                window={window}
                index={index}
                dispatch={dispatch}
                removable={editor.windowCountEditable}
              />
            ))}
            {editor.windowCountEditable && !routine.windows.length && (
              <Notice tone="warn">{editor.noHoursWarning}</Notice>
            )}
            {editor.windowCountEditable && (
              <Button
                title="Añadir horario"
                variant="outline"
                icon="plus"
                onPress={() => dispatch({ type: "addWindow" })}
              />
            )}
          </Section>
        )}

        {editor.chargingStart && (
          <Section title="Inicio anticipado al cargar">
            <Card className="gap-3 p-4">
              <TimeField
                label="Al conectar el cargador, el descanso comienza a partir de"
                value={routine.earlyStart}
                onChange={(earlyStart) => dispatch({ type: "setEarlyStart", earlyStart })}
              />
              {earlyStartInvalid ? (
                <FieldError>Elige las {earliestChargingStart} o una hora posterior.</FieldError>
              ) : (
                <Text className="px-1 text-sm text-muted">
                  A partir de las {earliestChargingStart}. Desconectar el cargador no cancela el
                  descanso.
                </Text>
              )}
            </Card>
          </Section>
        )}

        <Section title="Aplicaciones">
          <Card>
            {editor.appList && (
              <Row
                icon={editor.appList === "allowed" ? "check-circle-outline" : "cancel"}
                title={`Aplicaciones ${editor.appList === "allowed" ? "permitidas" : "bloqueadas"}`}
                subtitle={appNames(routine.apps, apps)}
                onPress={() => navigation.navigate("AppPicker", { list: "apps" })}
              />
            )}
            {editor.appList && editor.colorExceptions && <Divider />}
            {editor.colorExceptions && (
              <Row
                icon="palette-outline"
                title="Conservar el color en"
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
                title="Solo hoy"
                variant="secondary"
                disabled={!canSaveToday}
                onPress={() => save(true)}
              />
            </View>
          )}
          <View className="flex-1">
            <Button
              title={editor.session ? "Guardar" : "Guardar semanal"}
              disabled={!canSaveWeekly}
              onPress={() => save(false)}
            />
          </View>
        </View>
      </BottomBar>
    </KeyboardScreen>
  );
}
