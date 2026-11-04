import type { ExpoConfig } from "expo/config";
const version = "0.1.1";
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
  plugins: [["expo-build-properties", { android: { minSdkVersion: 35 } }]],
} satisfies ExpoConfig;
