import React, { useEffect, useMemo, useState } from "react";
import { FlatList, Image, Pressable, Text, View } from "react-native";
import { editors } from "../model";
import type { ScreenProps } from "../navigation";
import { useDraft, useStore } from "../store";
import { colors } from "../theme";
import { BottomBar, Button, Icon, KeyboardScreen, SearchInput } from "../ui";

const rowHeight = 64;

export function AppPicker({ navigation, route }: ScreenProps<"AppPicker">) {
  const { list } = route.params;
  const { apps, icons, loadIcons } = useStore();
  const { draft, dispatch } = useDraft();
  const [query, setQuery] = useState("");
  const selected = draft?.routine[list] ?? [];
  const routineId = draft?.routine.id;
  useEffect(() => {
    loadIcons();
  }, [loadIcons]);
  useEffect(() => {
    if (routineId)
      navigation.setOptions({
        title:
          list === "colorApps"
            ? "Conservar el color en"
            : `Aplicaciones ${editors[routineId].appList === "allowed" ? "permitidas" : "bloqueadas"}`,
      });
  }, [navigation, list, routineId]);
  // Ordered once on open, so a row doesn't jump away from the finger when it is toggled.
  const [initial] = useState(() => new Set(selected));
  const ordered = useMemo(
    () =>
      [...apps].sort(
        (a, b) => Number(initial.has(b.packageName)) - Number(initial.has(a.packageName)),
      ),
    [apps, initial],
  );
  const sharedLabels = useMemo(() => {
    const labels = apps.map((app) => app.label);
    return new Set(labels.filter((label, index) => labels.indexOf(label) !== index));
  }, [apps]);
  const needle = query.trim().toLowerCase();
  const visible = ordered.filter((app) =>
    `${app.label} ${app.packageName}`.toLowerCase().includes(needle),
  );
  if (!draft) return null;
  return (
    <KeyboardScreen>
      <View className="px-4 pb-2 pt-2">
        <SearchInput value={query} onChangeText={setQuery} placeholder="Buscar aplicaciones" />
      </View>
      <FlatList
        className="flex-1"
        data={visible}
        extraData={selected}
        keyboardShouldPersistTaps="handled"
        keyExtractor={(app) => app.packageName}
        getItemLayout={(_, index) => ({ length: rowHeight, offset: rowHeight * index, index })}
        ListEmptyComponent={
          <Text className="px-4 py-10 text-center text-base text-muted">
            No hay aplicaciones que coincidan con "{query}".
          </Text>
        }
        renderItem={({ item }) => {
          const checked = selected.includes(item.packageName);
          const icon = icons[item.packageName];
          return (
            <Pressable
              accessibilityRole="checkbox"
              accessibilityLabel={item.label}
              accessibilityState={{ checked }}
              android_ripple={{ color: "rgba(255,255,255,0.12)" }}
              onPress={() => dispatch({ type: "toggleApp", list, packageName: item.packageName })}
              style={{ height: rowHeight }}
              className="flex-row items-center gap-3 px-4"
            >
              {icon ? (
                <Image source={{ uri: icon }} className="h-10 w-10" />
              ) : (
                <View className="h-10 w-10 items-center justify-center rounded-xl bg-raised">
                  <Text className="text-base font-semibold text-muted">
                    {item.label.charAt(0).toUpperCase()}
                  </Text>
                </View>
              )}
              <View className="flex-1">
                <Text numberOfLines={1} className="text-base text-ink">
                  {item.label}
                </Text>
                {sharedLabels.has(item.label) && (
                  <Text numberOfLines={1} className="text-sm text-muted">
                    {item.packageName}
                  </Text>
                )}
              </View>
              <Icon
                name={checked ? "checkbox-marked-circle" : "checkbox-blank-circle-outline"}
                size={26}
                color={checked ? colors.accent : colors.muted}
              />
            </Pressable>
          );
        }}
      />
      <BottomBar>
        <Button
          title={`Listo · ${selected.length} seleccionadas`}
          onPress={() => navigation.goBack()}
        />
      </BottomBar>
    </KeyboardScreen>
  );
}
