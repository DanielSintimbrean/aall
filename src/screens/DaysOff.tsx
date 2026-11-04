import React, { useState } from "react";
import { Alert, Text, ToastAndroid, View } from "react-native";
import { canBookDay, dateLabel, earliestDay, localDate } from "../model";
import { native } from "../native";
import { useStore } from "../store";
import { Button, Card, DateField, Divider, Notice, Page, Row, Section } from "../ui";

export function DaysOff() {
  const { state, act } = useStore();
  const earliest = earliestDay();
  const [date, setDate] = useState(earliest);
  const today = localDate(new Date());
  const booked = state.daysOff.filter((day) => day >= today).sort();
  const alreadyBooked = booked.includes(date);
  const cancel = (day: string) =>
    Alert.alert(
      `Cancel ${dateLabel(day)}?`,
      canBookDay(day)
        ? "You can book it again while it's still 72 hours away."
        : "It's less than 72 hours away, so you won't be able to book it again.",
      [
        { text: "Keep booking", style: "cancel" },
        {
          text: "Cancel day off",
          style: "destructive",
          onPress: () => act(() => native!.cancelDay(day)),
        },
      ],
    );
  return (
    <Page>
      <Text className="px-1 text-base leading-6 text-muted">
        Restrictions pause from midnight to midnight. Book at least 72 hours ahead.
      </Text>
      <Section title="Book a day">
        <Card className="gap-3 p-4">
          <View className="flex-row">
            <DateField
              label="Date"
              value={date}
              display={dateLabel(date)}
              minimum={earliest}
              onChange={setDate}
            />
          </View>
          <Text className="px-1 text-sm text-muted">Earliest available: {dateLabel(earliest)}</Text>
          <Button
            title={alreadyBooked ? "Already booked" : "Book day off"}
            icon="calendar-plus"
            disabled={alreadyBooked}
            onPress={() => {
              if (act(() => native!.bookDay(date)))
                ToastAndroid.show(`Booked ${dateLabel(date)}`, ToastAndroid.SHORT);
            }}
          />
        </Card>
      </Section>
      <Section title="Booked">
        {booked.length ? (
          <Card>
            {booked.map((day, index) => (
              <View key={day}>
                {index > 0 && <Divider />}
                <Row
                  icon="calendar-check"
                  title={dateLabel(day)}
                  subtitle={day === today ? "Today" : undefined}
                  trailing={
                    <Button title="Cancel" variant="danger" compact onPress={() => cancel(day)} />
                  }
                />
              </View>
            ))}
          </Card>
        ) : (
          <Notice icon="calendar-blank-outline">No days off booked.</Notice>
        )}
      </Section>
    </Page>
  );
}
