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
        ToastAndroid.show("Paused for 15 minutes", ToastAndroid.SHORT);
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
          The right answer lifts all restrictions for 15 minutes. Current routines resume afterward.
        </Text>
        <Card className="items-center px-4 py-8">
          <Text className="text-center text-4xl font-bold text-ink">{expression}</Text>
        </Card>
        <View>
          <Input
            label="Answer"
            autoFocus
            keyboardType="numeric"
            returnKeyType="done"
            value={answer}
            error={wrong ? "Not quite. The sum stays the same until you solve it." : undefined}
            onChangeText={(value) => {
              setAnswer(value);
              setWrong(false);
            }}
            onSubmitEditing={() => answer.trim() && check()}
          />
        </View>
      </ScrollView>
      <BottomBar>
        <Button title="Check answer" disabled={!answer.trim()} onPress={check} />
      </BottomBar>
    </KeyboardScreen>
  );
}
