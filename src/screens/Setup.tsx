import React from "react";
import { Text, View } from "react-native";
import { native } from "../native";
import { useStore } from "../store";
import { colors } from "../theme";
import { Button, Card, Divider, Icon, Notice, Page, Row, Section, type IconName } from "../ui";

const adbCommand = "adb shell pm grant dev.danields.aall android.permission.WRITE_SECURE_SETTINGS";
const permissions: {
  kind: "accessibility" | "dnd" | "alarms" | "notifications" | "grayscale";
  icon: IconName;
  title: string;
  why: string;
}[] = [
  {
    kind: "accessibility",
    icon: "shield-lock-outline",
    title: "Bloqueo de aplicaciones",
    why: "El permiso de accesibilidad detecta qué aplicación está abierta.",
  },
  {
    kind: "dnd",
    icon: "bell-off-outline",
    title: "No molestar",
    why: "Se utiliza en Concentración y Descanso.",
  },
  {
    kind: "alarms",
    icon: "alarm",
    title: "Alarmas exactas",
    why: "Las rutinas empiezan y terminan a la hora prevista.",
  },
  {
    kind: "notifications",
    icon: "bell-outline",
    title: "Notificaciones",
    why: "Muestra qué está activo y cuándo termina.",
  },
  {
    kind: "grayscale",
    icon: "palette-outline",
    title: "Escala de grises",
    why: "Requiere conceder un permiso una sola vez mediante ADB.",
  },
];

export function Setup() {
  const { state, act } = useStore();
  const granted = state.permissions;
  return (
    <Page>
      <Section title="Permisos">
        <Card>
          {permissions.map((permission, index) => {
            const ok = granted[permission.kind];
            const adbOnly = permission.kind === "grayscale";
            return (
              <View key={permission.kind}>
                {index > 0 && <Divider />}
                <Row
                  icon={permission.icon}
                  title={permission.title}
                  subtitle={permission.why}
                  accessibilityLabel={`${permission.title}, ${ok ? "concedido" : "requiere configuración"}`}
                  chevron={!adbOnly}
                  trailing={
                    <Icon
                      name={ok ? "check-circle" : "alert-circle-outline"}
                      color={ok ? colors.accent : colors.warn}
                    />
                  }
                  onPress={
                    adbOnly ? undefined : () => act(() => native!.openSettings(permission.kind))
                  }
                />
              </View>
            );
          })}
        </Card>
      </Section>
      {!granted.accessibility && (
        <Notice tone="warn">
          Para activar la accesibilidad en aplicaciones instaladas desde un APK, puede ser necesario
          activar Permitir ajustes restringidos en la información de la aplicación.
        </Notice>
      )}
      {!granted.grayscale && (
        <Section title="Conceder permiso de escala de grises mediante ADB">
          <Card className="p-4">
            <Text selectable className="font-mono text-sm leading-5 text-ink">
              {adbCommand}
            </Text>
          </Card>
        </Section>
      )}
      <Section title="Comprobar escala de grises">
        <View className="gap-2">
          <Button
            title="Probar durante 10 segundos"
            variant="secondary"
            icon="palette-outline"
            disabled={!granted.grayscale}
            onPress={() => act(() => native!.testGrayscale(true))}
          />
          <Button
            title="Restaurar el color ahora"
            variant="outline"
            disabled={!granted.grayscale}
            onPress={() => act(() => native!.testGrayscale(false))}
          />
        </View>
      </Section>
    </Page>
  );
}
