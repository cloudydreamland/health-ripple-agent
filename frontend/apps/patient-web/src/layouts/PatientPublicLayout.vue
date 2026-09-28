<script setup lang="ts">
import PatientHeader from "../components/PatientHeader.vue";
import { useRoute } from "vue-router";

const route = useRoute();
</script>

<template>
  <main class="patient-page theme-patient" :class="{ 'patient-login-shell': route.path === '/login' }">
    <PatientHeader public-mode />
    <RouterView />
  </main>
</template>
