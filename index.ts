import { registerRootComponent } from "expo";
import { AppRegistry } from "react-native";

import App from "./App";
import { apps } from "./src/apps";

registerRootComponent(App);
for (const { task } of apps) if (task) AppRegistry.registerHeadlessTask(task.name, () => task.run);
