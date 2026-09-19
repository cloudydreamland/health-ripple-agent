import { createApp } from "vue";
import App from "./App.vue";
/* 真字体本地化打包（离线可用）：衬线用 Noto Serif SC，数据用 JetBrains Mono */
import "@fontsource/noto-serif-sc/600.css";
import "@fontsource/noto-serif-sc/700.css";
import "@fontsource/noto-serif-sc/900.css";
import "@fontsource/jetbrains-mono/400.css";
import "@fontsource/jetbrains-mono/700.css";
import "./style.css";

createApp(App).mount("#app");
