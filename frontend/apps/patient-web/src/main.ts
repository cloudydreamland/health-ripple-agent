import { createApp } from "vue";
import { createPinia } from "pinia";
import PrimeVue from "primevue/config";
import { patientPreset } from "./primevue-theme";
import App from "./App.vue";
import router from "./router";
import "./style.css";
import "./triage-pilot.css";
import "./modern.css";

createApp(App)
  .use(createPinia())
  .use(router)
  .use(PrimeVue, { theme: { preset: patientPreset, options: { darkModeSelector: "none" } } })
  .mount("#app");
