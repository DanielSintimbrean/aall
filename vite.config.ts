import { defineConfig } from "vite-plus";
export default defineConfig({
  test: { include: ["src/**/*.test.ts"], environment: "node" },
  lint: {
    plugins: ["typescript", "unicorn", "oxc", "react", "import", "vitest"],
    categories: { correctness: "error", suspicious: "error" },
    options: { typeAware: true, denyWarnings: true, reportUnusedDisableDirectives: "error" },
    rules: {
      // Global CSS is imported for its side effect.
      "import/no-unassigned-import": ["error", { allow: ["**/*.css"] }],
      // expo-status-bar takes its style as a string.
      "react/style-prop-object": ["error", { allow: ["StatusBar"] }],
      // Sorted arrays here are always fresh copies, and Hermes support for toSorted is unverified.
      "unicorn/no-array-sort": "off",
      // Prop and store types use method shorthand, and no implementation reads `this`.
      "typescript/unbound-method": "off",
    },
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
