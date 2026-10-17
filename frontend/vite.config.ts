import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ command }) => ({
  plugins: [react()],
  base: command === "serve" ? "/" : "/js/react/",
  build: {
    outDir: "../enterprise-app/target/generated-resources/frontend/js/react",
    emptyOutDir: true,
    sourcemap: false,
  },
  server: {
    proxy: {
      "/backend": {
        target: process.env.FLOWSTAFF_BACKEND || "http://localhost:8080",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/backend/, ""),
        configure: (proxy) =>
          proxy.on("proxyRes", (response) => {
            if (response.headers.location) {
              const target = new URL(
                process.env.FLOWSTAFF_BACKEND || "http://localhost:8080",
              );
              const redirect = new URL(response.headers.location, target);
              if (redirect.origin === target.origin)
                response.headers.location =
                  "/backend" + redirect.pathname + redirect.search;
            }
          }),
      },
    },
  },
}));
