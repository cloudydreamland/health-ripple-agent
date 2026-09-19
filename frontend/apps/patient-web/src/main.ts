import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import router from "./router";
/* 真字体本地化打包（离线可用）：衬线 Noto Serif SC */
import "@fontsource/noto-serif-sc/600.css";
import "@fontsource/noto-serif-sc/700.css";
import "@fontsource/noto-serif-sc/900.css";
import "./style.css";

createApp(App).use(createPinia()).use(router).mount("#app");
