import "./global.css";
import React from "react";
import { ActivityIndicator, Text, View } from "react-native";
import { DarkTheme, NavigationContainer, type Theme } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { QueryClientProvider } from "@tanstack/react-query";
import {
  SafeAreaProvider,
  SafeAreaView as NativeSafeAreaView,
} from "react-native-safe-area-context";
import { StatusBar } from "expo-status-bar";
import { withUniwind } from "uniwind";
import { apps } from "./src/apps";
import type { Routes } from "./src/navigation";
import { queryClient } from "./src/query";
import { AppPicker } from "./src/screens/AppPicker";
import { Bypass } from "./src/screens/Bypass";
import { DaysOff } from "./src/screens/DaysOff";
import { RoutineEditor } from "./src/screens/RoutineEditor";
import { Setup } from "./src/screens/Setup";
import { Today } from "./src/screens/Today";
import { StoreProvider } from "./src/store";
import { colors } from "./src/theme";

const SafeAreaView = withUniwind(NativeSafeAreaView);
const Stack = createNativeStackNavigator<Routes>();
const theme: Theme = {
  ...DarkTheme,
  colors: {
    ...DarkTheme.colors,
    background: colors.canvas,
    card: colors.canvas,
    text: colors.ink,
    primary: colors.accent,
    border: colors.line,
  },
};

const missingNativeModule = (
  <SafeAreaView className="flex-1 gap-2 bg-canvas p-6">
    <Text className="text-2xl font-semibold text-ink">Instala el APK de aall para Android</Text>
    <Text className="text-base text-muted">Expo Go no puede ejecutar aall.</Text>
  </SafeAreaView>
);

const loading = (
  <View className="flex-1 items-center justify-center gap-4 bg-canvas">
    <Text className="text-3xl font-semibold text-ink">aall</Text>
    <ActivityIndicator color={colors.accent} />
  </View>
);

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <SafeAreaProvider>
        <StatusBar style="light" />
        <StoreProvider fallback={missingNativeModule} loading={loading}>
          <NavigationContainer theme={theme}>
            <Stack.Navigator
              screenOptions={{
                animation: "slide_from_right",
                headerShadowVisible: false,
                headerStyle: { backgroundColor: colors.canvas },
                headerTintColor: colors.ink,
                contentStyle: { backgroundColor: colors.canvas },
              }}
            >
              <Stack.Screen name="Today" component={Today} options={{ headerShown: false }} />
              <Stack.Screen
                name="Routine"
                component={RoutineEditor}
                options={({ route }) => ({ title: route.params.title })}
              />
              <Stack.Screen
                name="AppPicker"
                component={AppPicker}
                options={{ title: "Elegir aplicaciones" }}
              />
              <Stack.Screen name="Bypass" component={Bypass} options={{ title: "Pausa" }} />
              <Stack.Screen name="DaysOff" component={DaysOff} options={{ title: "Días libres" }} />
              <Stack.Screen
                name="Setup"
                component={Setup}
                options={{ title: "Configuración del teléfono" }}
              />
              {apps.map((app) => (
                <Stack.Screen
                  key={app.route}
                  name={app.route}
                  component={app.screen}
                  options={{ title: app.title }}
                />
              ))}
            </Stack.Navigator>
          </NavigationContainer>
        </StoreProvider>
      </SafeAreaProvider>
    </QueryClientProvider>
  );
}
