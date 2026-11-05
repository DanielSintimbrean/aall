import { requireNativeModule } from "expo-modules-core";
import { useFocusEffect } from "@react-navigation/native";
import React, { useCallback, useState } from "react";
import { Switch, Text, View } from "react-native";
import { native as automation } from "../../native";
import { colors } from "../../theme";
import { Button, Card, Divider, Notice, Page, Section } from "../../ui";

type Departure = {
  line: string;
  destination: string;
  clock: string;
  minutes: number;
  source: string | null;
  scheduledClock: string | null;
};
type Native = {
  board(): Promise<string>;
  isRunning(): boolean;
  setRunning(running: boolean): void;
  notificationsEnabled(): boolean;
};

const native = requireNativeModule<Native>("AallMovent");
const REFRESH_MS = 30_000;

export function Movent() {
  const [departures, setDepartures] = useState<Departure[]>();
  const [failed, setFailed] = useState(false);
  const [running, setRunning] = useState(() => native.isRunning());
  const [notifications, setNotifications] = useState(() => native.notificationsEnabled());

  useFocusEffect(
    useCallback(() => {
      let current = true;
      const load = () => {
        setRunning(native.isRunning());
        setNotifications(native.notificationsEnabled());
        native.board().then(
          (raw) => {
            if (!current) return;
            setDepartures(JSON.parse(raw));
            setFailed(false);
          },
          () => {
            if (current) setFailed(true);
          },
        );
      };
      load();
      const timer = setInterval(load, REFRESH_MS);
      return () => {
        current = false;
        clearInterval(timer);
      };
    }, []),
  );

  const toggle = (next: boolean) => {
    native.setRunning(next);
    setRunning(next);
  };

  return (
    <Page>
      <Card className="flex-row items-center gap-3 p-4">
        <View className="flex-1 gap-1">
          <Text className="text-base font-semibold text-ink">Mostrar en notificaciones</Text>
          <Text className="text-sm text-muted">
            Se actualiza cada minuto con la pantalla encendida.
          </Text>
        </View>
        <Switch
          accessibilityLabel="Mostrar en notificaciones"
          value={running}
          onValueChange={toggle}
          trackColor={{ true: colors.accent }}
        />
      </Card>
      {running && !notifications && (
        <>
          <Notice tone="warn">Las notificaciones de aall están desactivadas.</Notice>
          <Button
            title="Activar notificaciones"
            variant="secondary"
            onPress={() => automation?.openSettings("notifications")}
          />
        </>
      )}
      {failed && <Notice tone="warn">No se ha podido consultar Mou-te.</Notice>}
      <Section title="Cascada de les Moles · 126">
        <Card>
          {!departures && !failed && <Message>Buscando buses…</Message>}
          {departures?.length === 0 && <Message>Sin buses en las próximas 2 horas.</Message>}
          {departures?.map((departure, index) => (
            <View key={`${departure.line}-${departure.clock}-${index}`}>
              {index > 0 && <Divider />}
              <DepartureRow departure={departure} />
            </View>
          ))}
        </Card>
      </Section>
    </Page>
  );
}

function Message({ children }: { children: string }) {
  return <Text className="p-4 text-base text-muted">{children}</Text>;
}

function DepartureRow({ departure }: { departure: Departure }) {
  return (
    <View className="flex-row items-center gap-3 p-4">
      <View className="w-14">
        <Text className="text-xl font-bold text-ink">{departure.clock}</Text>
        {departure.scheduledClock && (
          <Text className="text-xs text-muted line-through">{departure.scheduledClock}</Text>
        )}
      </View>
      <View className="flex-1">
        <Text className="text-base font-semibold text-ink">{departure.line}</Text>
        <Text className="text-sm text-muted">{departure.destination}</Text>
      </View>
      <View className="items-end">
        <Text className="text-base font-semibold text-ink">
          {departure.minutes ? `${departure.minutes} min` : "Ahora"}
        </Text>
        {departure.source && (
          <Text className={`text-xs ${departure.source === "GPS" ? "text-accent" : "text-muted"}`}>
            {departure.source}
          </Text>
        )}
      </View>
    </View>
  );
}
