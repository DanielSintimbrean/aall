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
    title: "App blocking",
    why: "Accessibility access detects which app is open.",
  },
  {
    kind: "dnd",
    icon: "bell-off-outline",
    title: "Do Not Disturb",
    why: "Used by Focus and Bedtime.",
  },
  {
    kind: "alarms",
    icon: "alarm",
    title: "Exact alarms",
    why: "Routines start and end on time.",
  },
  {
    kind: "notifications",
    icon: "bell-outline",
    title: "Notifications",
    why: "Shows what is active and when it ends.",
  },
  {
    kind: "grayscale",
    icon: "palette-outline",
    title: "Grayscale",
    why: "Needs a one-time grant over ADB.",
  },
];

export function Setup() {
  const { state, act } = useStore();
  const granted = state.permissions;
  return (
    <Page>
      <Section title="Permissions">
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
                  accessibilityLabel={`${permission.title}, ${ok ? "granted" : "needs setup"}`}
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
          Sideloaded apps may need Allow restricted settings in app info before accessibility can be
          enabled.
        </Notice>
      )}
      {!granted.grayscale && (
        <Section title="Grant grayscale over ADB">
          <Card className="p-4">
            <Text selectable className="font-mono text-sm leading-5 text-ink">
              {adbCommand}
            </Text>
          </Card>
        </Section>
      )}
      <Section title="Check grayscale">
        <View className="gap-2">
          <Button
            title="Test for 10 seconds"
            variant="secondary"
            icon="palette-outline"
            disabled={!granted.grayscale}
            onPress={() => act(() => native!.testGrayscale(true))}
          />
          <Button
            title="Restore color now"
            variant="outline"
            disabled={!granted.grayscale}
            onPress={() => act(() => native!.testGrayscale(false))}
          />
        </View>
      </Section>
    </Page>
  );
}
