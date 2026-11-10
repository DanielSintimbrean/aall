import { focusManager, QueryClient } from "@tanstack/react-query";
import { AppState } from "react-native";

export const queryClient = new QueryClient();

focusManager.setEventListener((setFocused) => {
  const subscription = AppState.addEventListener("change", (state) =>
    setFocused(state === "active"),
  );
  return () => subscription.remove();
});
