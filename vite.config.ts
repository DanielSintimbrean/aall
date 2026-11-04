import { defineConfig } from "vite-plus";
export default defineConfig({
  test: { include: ["src/**/*.test.ts"], environment: "node" },
  lint: {
    ignorePatterns: [
      "android/**",
      "modules/**/build/**",
      "node_modules/**",
      "src/uniwind-types.d.ts",
    ],
  },
  fmt: {
    ignorePatterns: ["android/**", "node_modules/**", "pnpm-lock.yaml", "src/uniwind-types.d.ts"],
  },
});
