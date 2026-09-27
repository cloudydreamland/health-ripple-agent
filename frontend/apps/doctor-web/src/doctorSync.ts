import type { InjectionKey, Ref } from "vue";

export interface DoctorSyncState {
  hasSynced: Ref<boolean>;
  syncError: Ref<string>;
  lastSyncedAt: Ref<string>;
  connectionStatus: Ref<string>;
}

export const doctorSyncKey: InjectionKey<DoctorSyncState> = Symbol("doctor-sync");
