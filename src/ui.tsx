import React, { useEffect, useState, type ComponentProps, type ReactNode } from "react";
import { MaterialCommunityIcons } from "@expo/vector-icons";
import { DateTimePickerAndroid } from "@react-native-community/datetimepicker";
import { Dimensions, Keyboard, Pressable, ScrollView, Text, TextInput, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import {
  dateToTime,
  localDate,
  parseDate,
  statusLabels,
  timeToDate,
  weekdays,
  type RoutineStatus,
} from "./model";
import { colors } from "./theme";

export type IconName = ComponentProps<typeof MaterialCommunityIcons>["name"];
export function Icon({
  name,
  size = 22,
  color = colors.muted,
}: {
  name: IconName;
  size?: number;
  color?: string;
}) {
  return (
    <MaterialCommunityIcons
      name={name}
      size={size}
      color={color}
      accessible={false}
      importantForAccessibility="no"
    />
  );
}

const ripple = { color: "rgba(255,255,255,0.12)", foreground: true };

const buttonStyles = {
  primary: { box: "bg-accent", text: "text-on-accent", icon: colors.onAccent },
  secondary: { box: "bg-raised", text: "text-ink", icon: colors.ink },
  outline: { box: "border border-line", text: "text-ink", icon: colors.ink },
  danger: { box: "border border-line", text: "text-danger", icon: colors.danger },
};
export function Button({
  title,
  onPress,
  variant = "primary",
  icon,
  disabled,
  compact,
}: {
  title: string;
  onPress(): void;
  variant?: keyof typeof buttonStyles;
  icon?: IconName;
  disabled?: boolean;
  compact?: boolean;
}) {
  const style = disabled
    ? { box: "bg-surface", text: "text-muted", icon: colors.muted }
    : buttonStyles[variant];
  return (
    <View className="overflow-hidden rounded-2xl">
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ disabled: !!disabled }}
        android_ripple={ripple}
        disabled={disabled}
        onPress={onPress}
        className={`flex-row items-center justify-center gap-2 rounded-2xl px-5 ${compact ? "min-h-11" : "min-h-14"} ${style.box}`}
      >
        {icon && <Icon name={icon} size={20} color={style.icon} />}
        <Text className={`text-base font-semibold ${style.text}`}>{title}</Text>
      </Pressable>
    </View>
  );
}

/** Keeps pinned actions above the keyboard. The edge-to-edge window doesn't resize for it. */
export function KeyboardScreen({ children }: { children: ReactNode }) {
  const [height, setHeight] = useState(0);
  useEffect(() => {
    const shown = Keyboard.addListener("keyboardDidShow", (event) =>
      setHeight(Math.max(0, Dimensions.get("screen").height - event.endCoordinates.screenY)),
    );
    const hidden = Keyboard.addListener("keyboardDidHide", () => setHeight(0));
    return () => {
      shown.remove();
      hidden.remove();
    };
  }, []);
  return (
    <View className="flex-1 bg-canvas" style={{ paddingBottom: height }}>
      {children}
    </View>
  );
}

/** Scrolling page content that clears the gesture bar when nothing is pinned below it. */
export function Page({ children, bare }: { children: ReactNode; bare?: boolean }) {
  const insets = useSafeAreaInsets();
  return (
    <ScrollView
      className="flex-1 bg-canvas"
      contentContainerClassName="gap-6 px-4 pt-2"
      contentContainerStyle={{ paddingBottom: (bare ? 0 : insets.bottom) + 24 }}
      keyboardShouldPersistTaps="handled"
    >
      {children}
    </ScrollView>
  );
}

export function BottomBar({ children }: { children: ReactNode }) {
  const insets = useSafeAreaInsets();
  return (
    <View
      className="gap-2 border-t border-line bg-canvas px-4 pt-3"
      style={{ paddingBottom: insets.bottom + 12 }}
    >
      {children}
    </View>
  );
}

export function Section({
  title,
  action,
  children,
}: {
  title?: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <View className="gap-2">
      {(title || action) && (
        <View className="min-h-7 flex-row items-center justify-between px-1">
          <Text className="text-sm font-semibold text-muted">{title}</Text>
          {action}
        </View>
      )}
      {children}
    </View>
  );
}

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <View className={`overflow-hidden rounded-3xl bg-surface ${className}`}>{children}</View>;
}

export function Divider() {
  return <View className="ml-[68px] h-px bg-line" />;
}

export function Row({
  icon,
  title,
  subtitle,
  trailing,
  onPress,
  chevron = !!onPress,
  accessibilityLabel,
}: {
  icon?: IconName;
  title: string;
  subtitle?: string;
  trailing?: ReactNode;
  onPress?(): void;
  chevron?: boolean;
  accessibilityLabel?: string;
}) {
  return (
    <Pressable
      accessibilityRole={onPress ? "button" : undefined}
      accessibilityLabel={accessibilityLabel}
      android_ripple={onPress ? ripple : undefined}
      disabled={!onPress}
      onPress={onPress}
      className="min-h-[68px] flex-row items-center gap-3 px-4 py-3"
    >
      {icon && (
        <View className="h-10 w-10 items-center justify-center rounded-xl bg-raised">
          <Icon name={icon} color={colors.ink} />
        </View>
      )}
      <View className="flex-1">
        <Text className="text-base font-semibold text-ink">{title}</Text>
        {subtitle && <Text className="mt-0.5 text-sm text-muted">{subtitle}</Text>}
      </View>
      {trailing}
      {chevron && <Icon name="chevron-right" />}
    </Pressable>
  );
}

const pillStyles: Record<
  RoutineStatus,
  { box: string; text: string; icon?: IconName; color?: string }
> = {
  disabled: { box: "", text: "text-muted" },
  ready: { box: "bg-raised", text: "text-ink" },
  active: {
    box: "bg-accent",
    text: "text-on-accent",
    icon: "circle-medium",
    color: colors.onAccent,
  },
  needsSetup: {
    box: "bg-warn-soft",
    text: "text-warn",
    icon: "alert-circle-outline",
    color: colors.warn,
  },
};
export function StatusPill({ status }: { status: RoutineStatus }) {
  const style = pillStyles[status];
  return (
    <View className={`flex-row items-center gap-1 rounded-full px-2.5 py-1 ${style.box}`}>
      {style.icon && <Icon name={style.icon} size={14} color={style.color} />}
      <Text className={`text-xs font-semibold ${style.text}`}>{statusLabels[status]}</Text>
    </View>
  );
}

export function Notice({
  children,
  tone = "info",
  icon,
}: {
  children: ReactNode;
  tone?: "info" | "warn";
  icon?: IconName;
}) {
  const warn = tone === "warn";
  return (
    <View className={`flex-row gap-3 rounded-2xl p-4 ${warn ? "bg-warn-soft" : "bg-surface"}`}>
      <Icon
        name={icon ?? (warn ? "alert-circle-outline" : "information-outline")}
        size={20}
        color={warn ? colors.warn : colors.muted}
      />
      <Text className={`flex-1 text-sm leading-5 ${warn ? "text-warn" : "text-muted"}`}>
        {children}
      </Text>
    </View>
  );
}

export function FieldError({ children }: { children: ReactNode }) {
  return <Text className="px-1 text-sm text-danger">{children}</Text>;
}

export function Input({
  label,
  hint,
  error,
  ...props
}: ComponentProps<typeof TextInput> & { label: string; hint?: string; error?: string }) {
  const [focused, setFocused] = useState(false);
  const border = error ? "border-danger" : focused ? "border-accent" : "border-line";
  return (
    <View className="gap-1.5">
      <Text className="px-1 text-sm font-semibold text-muted">{label}</Text>
      <TextInput
        accessibilityLabel={label}
        autoCapitalize="none"
        autoCorrect={false}
        placeholderTextColor={colors.muted}
        selectionColor={colors.accent}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        className={`min-h-14 rounded-2xl border-2 bg-raised px-4 text-lg text-ink ${border}`}
        {...props}
      />
      {error ? (
        <FieldError>{error}</FieldError>
      ) : (
        hint && <Text className="px-1 text-sm text-muted">{hint}</Text>
      )}
    </View>
  );
}

export function SearchInput({
  value,
  onChangeText,
  placeholder,
}: {
  value: string;
  onChangeText(value: string): void;
  placeholder: string;
}) {
  return (
    <View className="min-h-12 flex-row items-center gap-2 rounded-2xl bg-raised pl-4 pr-1">
      <Icon name="magnify" />
      <TextInput
        accessibilityLabel={placeholder}
        autoCapitalize="none"
        autoCorrect={false}
        placeholder={placeholder}
        placeholderTextColor={colors.muted}
        selectionColor={colors.accent}
        value={value}
        onChangeText={onChangeText}
        className="min-h-12 flex-1 text-base text-ink"
      />
      {!!value && (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Clear search"
          hitSlop={8}
          onPress={() => onChangeText("")}
          className="h-10 w-10 items-center justify-center"
        >
          <Icon name="close-circle" size={20} />
        </Pressable>
      )}
    </View>
  );
}

function PickerField({
  label,
  value,
  icon,
  onPress,
}: {
  label: string;
  value: string;
  icon: IconName;
  onPress(): void;
}) {
  return (
    <View className="flex-1 overflow-hidden rounded-2xl">
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${label}, ${value}. Change`}
        android_ripple={ripple}
        onPress={onPress}
        className="min-h-[72px] justify-center rounded-2xl bg-raised px-4 py-2"
      >
        <Text className="text-sm text-muted">{label}</Text>
        <View className="flex-row items-center justify-between gap-2">
          <Text className="flex-shrink text-xl font-semibold text-ink">{value}</Text>
          <Icon name={icon} size={18} />
        </View>
      </Pressable>
    </View>
  );
}

export function TimeField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange(value: string): void;
}) {
  return (
    <PickerField
      label={label}
      value={value}
      icon="clock-outline"
      onPress={() =>
        DateTimePickerAndroid.open({
          mode: "time",
          is24Hour: true,
          value: timeToDate(value),
          onValueChange: (_, date) => onChange(dateToTime(date)),
        })
      }
    />
  );
}

export function DateField({
  label,
  value,
  display,
  minimum,
  onChange,
}: {
  label: string;
  value: string;
  display: string;
  minimum: string;
  onChange(value: string): void;
}) {
  return (
    <PickerField
      label={label}
      value={display}
      icon="calendar-month-outline"
      onPress={() =>
        DateTimePickerAndroid.open({
          mode: "date",
          value: parseDate(value) ?? new Date(),
          minimumDate: parseDate(minimum) ?? undefined,
          firstDayOfWeek: 1,
          onValueChange: (_, date) => onChange(localDate(date)),
        })
      }
    />
  );
}

export function DayPicker({ days, onChange }: { days: number[]; onChange(days: number[]): void }) {
  return (
    <View className="flex-row justify-between">
      {weekdays.map((day) => {
        const selected = days.includes(day.id);
        return (
          <Pressable
            key={day.id}
            accessibilityRole="checkbox"
            accessibilityLabel={day.name}
            accessibilityState={{ checked: selected }}
            hitSlop={2}
            onPress={() =>
              onChange(
                selected
                  ? days.filter((id) => id !== day.id)
                  : [...days, day.id].sort((a, b) => a - b),
              )
            }
            className={`h-11 w-11 items-center justify-center rounded-full ${selected ? "bg-accent" : "border border-line"}`}
          >
            <Text className={`text-sm font-semibold ${selected ? "text-on-accent" : "text-muted"}`}>
              {day.short}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

export function Stepper({
  value,
  onChange,
  label,
  error,
}: {
  value: string;
  onChange(value: string): void;
  label: string;
  error?: boolean;
}) {
  const step = (by: number) => {
    const current = Number(value) || 0;
    onChange(String(Math.min(1440, Math.max(1, current + by))));
  };
  const button = (icon: IconName, name: string, by: number) => (
    <View className="overflow-hidden rounded-2xl">
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={name}
        android_ripple={ripple}
        onPress={() => step(by)}
        className="h-14 w-14 items-center justify-center bg-raised"
      >
        <Icon name={icon} color={colors.ink} />
      </Pressable>
    </View>
  );
  return (
    <View className="flex-row items-center gap-2">
      {button("minus", "5 minutes less", -5)}
      <View
        className={`h-14 flex-1 flex-row items-center justify-center gap-1 rounded-2xl border-2 bg-raised ${error ? "border-danger" : "border-transparent"}`}
      >
        <TextInput
          accessibilityLabel={label}
          keyboardType="number-pad"
          maxLength={4}
          selectTextOnFocus
          selectionColor={colors.accent}
          value={value}
          onChangeText={onChange}
          className="min-w-10 text-center text-xl font-semibold text-ink"
        />
        <Text className="text-base text-muted">min</Text>
      </View>
      {button("plus", "5 minutes more", 5)}
    </View>
  );
}

export function Chip({
  label,
  selected,
  onPress,
}: {
  label: string;
  selected: boolean;
  onPress(): void;
}) {
  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityState={{ selected }}
      onPress={onPress}
      className={`min-h-10 flex-1 items-center justify-center rounded-full ${selected ? "bg-accent" : "border border-line"}`}
    >
      <Text className={`text-sm font-semibold ${selected ? "text-on-accent" : "text-ink"}`}>
        {label}
      </Text>
    </Pressable>
  );
}
