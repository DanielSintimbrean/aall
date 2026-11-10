import { useQuery } from "@tanstack/react-query";
import React from "react";
import { Linking, Switch, Text, View } from "react-native";
import { colors } from "../../theme";
import { Button, Card, Divider, Notice, Page, Section } from "../../ui";
import { clock, type Departure, minutes, source, time } from "./board";
import { native } from "./native";
import { boardQuery } from "./query";

const REFRESH_MS = 30_000;

export function Movent() {
  const board = useQuery({ ...boardQuery, refetchInterval: REFRESH_MS });
  const status = useQuery({
    queryKey: ["movent", "status"],
    queryFn: () => ({ running: native.isRunning(), notifications: native.notificationsEnabled() }),
    refetchInterval: REFRESH_MS,
  });
  const departures = board.data;
  const now = board.dataUpdatedAt;

  const toggle = (next: boolean) => {
    native.setRunning(next);
    void status.refetch();
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
          value={status.data?.running ?? false}
          onValueChange={toggle}
          trackColor={{ true: colors.accent }}
        />
      </Card>
      {status.data?.running && !status.data.notifications && (
        <>
          <Notice tone="warn">Las notificaciones de aall están desactivadas.</Notice>
          <Button
            title="Activar notificaciones"
            variant="secondary"
            onPress={() =>
              Linking.sendIntent("android.settings.APP_NOTIFICATION_SETTINGS", [
                { key: "android.provider.extra.APP_PACKAGE", value: "dev.danields.aall" },
              ])
            }
          />
        </>
      )}
      {board.isError && <Notice tone="warn">No se ha podido consultar Mou-te.</Notice>}
      <Section title="Cascada de les Moles · 126">
        <Card>
          {!departures && !board.isError && <Message>Buscando buses…</Message>}
          {departures?.length === 0 && <Message>Sin buses en las próximas 2 horas.</Message>}
          {departures?.map((departure, index) => (
            <View key={`${departure.line}-${departure.scheduled}-${index}`}>
              {index > 0 && <Divider />}
              <DepartureRow departure={departure} now={now} />
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

function DepartureRow({ departure, now }: { departure: Departure; now: number }) {
  const left = minutes(departure, now);
  const label = source(departure, now);
  return (
    <View className="flex-row items-center gap-3 p-4">
      <View>
        <Text className="text-xl font-bold text-ink">{clock(time(departure))}</Text>
        {time(departure) !== departure.scheduled && (
          <Text className="text-xs text-muted line-through">{clock(departure.scheduled)}</Text>
        )}
      </View>
      <View className="flex-1">
        <Text className="text-base font-semibold text-ink">{departure.line}</Text>
        <Text className="text-sm text-muted">{departure.destination}</Text>
      </View>
      <View className="items-end">
        <Text className="text-base font-semibold text-ink">{left ? `${left} min` : "Ahora"}</Text>
        {label && (
          <Text className={`text-xs ${label === "GPS" ? "text-accent" : "text-muted"}`}>
            {label}
          </Text>
        )}
      </View>
    </View>
  );
}
