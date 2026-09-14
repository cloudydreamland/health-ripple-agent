import vue from "@vitejs/plugin-vue";
import { defineConfig } from "vite";

const gatewayTarget = process.env.VITE_GATEWAY_TARGET ?? "http://localhost:18080";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5176,
    proxy: {
      "/api": gatewayTarget,
    },
  },
});
