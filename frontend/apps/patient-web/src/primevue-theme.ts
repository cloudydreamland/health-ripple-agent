import { definePreset } from "@primeuix/themes";
import Aura from "@primeuix/themes/aura";

// Patient Web only. The other applications keep their own visual systems.
export const patientPreset = definePreset(Aura, {
  primitive: {
    jade: {
      50: "#effaf6", 100: "#d9f3e9", 200: "#b8e9d7", 300: "#84d7bb",
      400: "#4bc19a", 500: "#17a678", 600: "#078762", 700: "#076f53",
      800: "#105943", 900: "#104b3a", 950: "#092e25",
    },
  },
  semantic: {
    primary: {
      50: "{jade.50}", 100: "{jade.100}", 200: "{jade.200}",
      300: "{jade.300}", 400: "{jade.400}", 500: "{jade.500}",
      600: "{jade.600}", 700: "{jade.700}", 800: "{jade.800}",
      900: "{jade.900}", 950: "{jade.950}",
    },
    colorScheme: {
      light: {
        primary: { color: "{jade.700}", hoverColor: "{jade.800}", activeColor: "{jade.900}", inverseColor: "#ffffff" },
        highlight: { background: "{jade.50}", focusBackground: "{jade.100}", color: "{jade.900}", focusColor: "{jade.950}" },
      },
    },
    focusRing: { width: "3px", style: "solid", color: "{jade.200}", offset: "2px" },
  },
  components: {
    button: { root: { borderRadius: "10px", paddingX: "1.15rem", paddingY: "0.75rem" } },
    inputtext: { root: { borderRadius: "10px", paddingX: "0.95rem", paddingY: "0.78rem" } },
    textarea: { root: { borderRadius: "10px", paddingX: "0.95rem", paddingY: "0.78rem" } },
    select: { root: { borderRadius: "10px" } },
    dialog: { root: { borderRadius: "16px" } },
  },
});
