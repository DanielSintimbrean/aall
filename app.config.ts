import type { ExpoConfig } from "expo/config";
const version = "0.1.3";
// Native dialogs can't read src/theme.ts at runtime, so their colors are set at build time.
const accent = "#5eead4";
const surface = "#151b1b";
const raised = "#212a2a";
const ink = "#f1f5f4";
const muted = "#a0aeac";
const onAccent = "#04211c";
const color = (value: string) => ({ light: value, dark: value });
export default {
  name: "aall",
  slug: "aall",
  version,
  orientation: "portrait",
  icon: "./assets/icon.png",
  userInterfaceStyle: "dark",
  android: {
    package: "dev.danields.aall",
    versionCode: Number(process.env.AALL_VERSION_CODE ?? 1),
    adaptiveIcon: { foregroundImage: "./assets/icon.png", backgroundColor: "#080b0b" },
    allowBackup: false,
  },
  plugins: [
    ["expo-build-properties", { android: { minSdkVersion: 35 } }],
    [
      "@react-native-community/datetimepicker",
      {
        android: {
          datePicker: {
            colorAccent: color(accent),
            colorControlActivated: color(accent),
            windowBackground: color(surface),
            textColorPrimary: color(ink),
            textColorPrimaryInverse: color(onAccent),
            textColorSecondary: color(muted),
          },
          timePicker: {
            background: color(surface),
            headerBackground: color(accent),
            numbersBackgroundColor: color(raised),
            numbersInnerTextColor: color(muted),
            numbersSelectorColor: color(accent),
            numbersTextColor: color(ink),
          },
        },
      },
    ],
    "expo-font",
  ],
} satisfies ExpoConfig;
