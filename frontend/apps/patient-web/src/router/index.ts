import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "@smart-cloud-brain/shared-api";
import PatientPublicLayout from "../layouts/PatientPublicLayout.vue";
import PatientPortalLayout from "../layouts/PatientPortalLayout.vue";
import LoginPage from "../pages/LoginPage.vue";
import RegisterPage from "../pages/RegisterPage.vue";
import PatientDashboard from "../pages/PatientDashboard.vue";
import TriagePage from "../pages/TriagePage.vue";
import DoctorSlotsPage from "../pages/DoctorSlotsPage.vue";
import AppointmentsPage from "../pages/AppointmentsPage.vue";
import VisitArchivePage from "../pages/VisitArchivePage.vue";
import MyRipplePage from "../pages/MyRipplePage.vue";
import ProfilePage from "../pages/ProfilePage.vue";
import FamilySharePage from "../pages/FamilySharePage.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      component: PatientPortalLayout,
      meta: { requiresAuth: true },
      children: [
        { path: "", name: "patient-dashboard", component: PatientDashboard },
        { path: "triage", name: "patient-triage", component: TriagePage },
        { path: "doctors", name: "patient-doctors", component: DoctorSlotsPage },
        { path: "appointments", name: "patient-appointments", component: AppointmentsPage },
        { path: "records", name: "patient-records", component: VisitArchivePage },
        { path: "prescriptions", redirect: (to) => ({ path: "/records", query: { ...to.query, focus: "prescriptions" } }) },
        { path: "ripple", name: "patient-ripple", component: MyRipplePage },
        { path: "profile", name: "patient-profile", component: ProfilePage },
      ],
    },
    {
      path: "/",
      component: PatientPublicLayout,
      children: [
        { path: "login", name: "patient-login", component: LoginPage },
        { path: "register", name: "patient-register", component: RegisterPage },
        // 家属守护圈：HMAC 令牌即凭证（免登录只读视图，最小披露口径）
        { path: "share/:token", name: "family-share", component: FamilySharePage },
      ],
    },
  ],
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  auth.load("patient-session", "PATIENT");
  if (to.meta.requiresAuth && (!auth.session || auth.permissionError)) {
    return { name: "patient-login", query: { redirect: to.fullPath } };
  }
  if ((to.name === "patient-login" || to.name === "patient-register") && auth.session && !auth.permissionError) {
    return { name: "patient-dashboard" };
  }
  return true;
});

export default router;
