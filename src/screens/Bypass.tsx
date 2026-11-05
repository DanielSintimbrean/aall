import React, { useState } from "react";
import { ScrollView, Text, ToastAndroid, View } from "react-native";
import type { ScreenProps } from "../navigation";
import { native } from "../native";
import { useStore } from "../store";
import { BottomBar, Button, Card, Input, KeyboardScreen } from "../ui";

export function Bypass({ navigation }: ScreenProps<"Bypass">) {
  const { act } = useStore();
  // Native keeps the same challenge until it is solved, so leaving doesn't re-roll it.
  const [expression] = useState(() => native!.challenge());
  const [answer, setAnswer] = useState("");
  const [wrong, setWrong] = useState(false);
  const check = () =>
    act(() => {
      if (native!.solve(answer)) {
        ToastAndroid.show("Restricciones pausadas durante 15 minutos", ToastAndroid.SHORT);
        navigation.popTo("Today");
      } else setWrong(true);
    });
  return (
    <KeyboardScreen>
      <ScrollView
        className="flex-1"
        contentContainerClassName="gap-6 p-4 pt-2"
        keyboardShouldPersistTaps="handled"
      >
        <Text className="px-1 text-base leading-6 text-muted">
          La respuesta correcta suspende todas las restricciones durante 15 minutos. Después se
          reanudan las rutinas.
        </Text>
        <Card className="items-center px-4 py-8">
          <Text className="text-center text-4xl font-bold text-ink">{expression}</Text>
        </Card>
        <View>
          <Input
            label="Respuesta"
            autoFocus
            keyboardType="numeric"
            returnKeyType="done"
            value={answer}
            error={
              wrong ? "Respuesta incorrecta. La suma no cambia hasta que la resuelvas." : undefined
            }
            onChangeText={(value) => {
              setAnswer(value);
              setWrong(false);
            }}
            onSubmitEditing={() => answer.trim() && check()}
          />
        </View>
      </ScrollView>
      <BottomBar>
        <Button title="Comprobar respuesta" disabled={!answer.trim()} onPress={check} />
      </BottomBar>
    </KeyboardScreen>
  );
}
