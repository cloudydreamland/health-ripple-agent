import type { DataRow } from "@smart-cloud-brain/shared-api";

export type VisitGroup = {
  id: string;
  record: DataRow;
  prescriptions: DataRow[];
};

export function archiveId(value: unknown): string | null {
  const raw = String(value ?? "").trim();
  return /^\d+$/.test(raw) && BigInt(raw) > 0n ? BigInt(raw).toString() : null;
}

function newestFirst(a: DataRow, b: DataRow, idKey: string): number {
  const aDate = Date.parse(String(a.createdAt ?? ""));
  const bDate = Date.parse(String(b.createdAt ?? ""));
  if (Number.isFinite(aDate) && Number.isFinite(bDate) && aDate !== bDate) return bDate - aDate;
  const aId = archiveId(a[idKey]) ?? "0";
  const bId = archiveId(b[idKey]) ?? "0";
  return BigInt(bId) > BigInt(aId) ? 1 : BigInt(bId) < BigInt(aId) ? -1 : 0;
}

export function groupVisitArchive(records: DataRow[], prescriptions: DataRow[]) {
  const visits: VisitGroup[] = [...records]
    .sort((a, b) => newestFirst(a, b, "medicalRecordId"))
    .map((record) => ({ id: archiveId(record.medicalRecordId) ?? "", record, prescriptions: [] }));
  const byRecordId = new Map(visits.filter((visit) => visit.id).map((visit) => [visit.id, visit]));
  const byRegistrationId = new Map<string, VisitGroup | null>();
  for (const visit of visits) {
    const registrationId = archiveId(visit.record.registrationId);
    if (!registrationId) continue;
    byRegistrationId.set(registrationId, byRegistrationId.has(registrationId) ? null : visit);
  }

  const unlinked: DataRow[] = [];
  for (const prescription of prescriptions) {
    const recordId = archiveId(prescription.medicalRecordId);
    const registrationId = archiveId(prescription.registrationId);
    const match = recordId
      ? byRecordId.get(recordId)
      : registrationId ? byRegistrationId.get(registrationId) : null;
    if (match) match.prescriptions.push(prescription);
    else unlinked.push(prescription);
  }
  for (const visit of visits) visit.prescriptions.sort((a, b) => newestFirst(a, b, "prescriptionId"));
  unlinked.sort((a, b) => newestFirst(a, b, "prescriptionId"));
  return { visits, unlinked };
}
