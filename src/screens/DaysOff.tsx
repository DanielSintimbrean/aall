import React, { useState } from "react";
import { Alert, Text, ToastAndroid, View } from "react-native";
import { dateLabel, localDate } from "../model";
import { native } from "../native";
import { useStore } from "../store";
import { Button, Card, DateField, Divider, Notice, Page, Row, Section } from "../ui";

export function DaysOff() {
  const { state, act } = useStore();
  const earliest = state.earliestDayOff;
  const [date, setDate] = useState(earliest);
  // oxlint-disable-next-line react/purity -- The snapshot poll re-renders with the current date.
  const today = localDate(new Date());
  const booked = state.daysOff.filter((day) => day >= today).sort();
  const alreadyBooked = booked.includes(date);
  const cancel = (day: string) =>
    Alert.alert(
      `¿Cancelar el ${dateLabel(day)}?`,
      day >= earliest
        ? "Puedes volver a reservarlo mientras falten al menos 72 horas."
        : "Faltan menos de 72 horas, así que no podrás volver a reservarlo.",
      [
        { text: "Mantener reserva", style: "cancel" },
        {
          text: "Cancelar día libre",
          style: "destructive",
          onPress: () => act(() => native!.cancelDay(day)),
        },
      ],
    );
  return (
    <Page>
      <Text className="px-1 text-base leading-6 text-muted">
        Las restricciones se suspenden de medianoche a medianoche. Reserva con al menos 72 horas de
        antelación.
      </Text>
      <Section title="Reservar un día">
        <Card className="gap-3 p-4">
          <View className="flex-row">
            <DateField
              label="Fecha"
              value={date}
              display={dateLabel(date)}
              minimum={earliest}
              onChange={setDate}
            />
          </View>
          <Text className="px-1 text-sm text-muted">
            Primera fecha disponible: {dateLabel(earliest)}
          </Text>
          <Button
            title={alreadyBooked ? "Ya reservado" : "Reservar día libre"}
            icon="calendar-plus"
            disabled={alreadyBooked}
            onPress={() => {
              if (act(() => native!.bookDay(date)))
                ToastAndroid.show(`Reservado el ${dateLabel(date)}`, ToastAndroid.SHORT);
            }}
          />
        </Card>
      </Section>
      <Section title="Reservas">
        {booked.length ? (
          <Card>
            {booked.map((day, index) => (
              <View key={day}>
                {index > 0 && <Divider />}
                <Row
                  icon="calendar-check"
                  title={dateLabel(day)}
                  subtitle={day === today ? "Hoy" : undefined}
                  trailing={
                    <Button title="Cancelar" variant="danger" compact onPress={() => cancel(day)} />
                  }
                />
              </View>
            ))}
          </Card>
        ) : (
          <Notice icon="calendar-blank-outline">No hay días libres reservados.</Notice>
        )}
      </Section>
    </Page>
  );
}
