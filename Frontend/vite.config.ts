import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Dev server proxies /api/* to the Spring Boot backend on :8080, so the browser
// talks same-origin (:3000) and we avoid CORS entirely. The `@/*` alias is read
// from tsconfig.json via Vite's native resolve.tsconfigPaths.
export default defineConfig({
  plugins: [react()],
  resolve: { tsconfigPaths: true },
  server: {
    port: 3000,
    proxy: {
      "/api": { target: "http://localhost:8080", changeOrigin: true },
    },
  },
  build: { outDir: "dist" },
});
