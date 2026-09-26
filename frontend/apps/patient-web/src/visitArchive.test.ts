import { describe, expect, it } from "vitest";
import { groupVisitArchive } from "./visitArchive";

describe("groupVisitArchive", () => {
  const records = [
    { medicalRecordId: 10, registrationId: 110, diagnosis: "甲" },
    { medicalRecordId: 11, registrationId: 111, diagnosis: "乙" },
  ];

  it("keeps visits without a prescription and groups multiple prescriptions", () => {
    const result = groupVisitArchive(records, [
      { prescriptionId: 1, medicalRecordId: 10 },
      { prescriptionId: 2, medicalRecordId: 10 },
    ]);
    expect(result.visits.find((visit) => visit.id === "10")?.prescriptions).toHaveLength(2);
    expect(result.visits.find((visit) => visit.id === "11")?.prescriptions).toHaveLength(0);
    expect(result.unlinked).toHaveLength(0);
  });

  it("uses a unique registration only when the record id is absent", () => {
    const result = groupVisitArchive(records, [
      { prescriptionId: 3, medicalRecordId: 0, registrationId: 110 },
      { prescriptionId: 4, medicalRecordId: 999, registrationId: 111 },
    ]);
    expect(result.visits.find((visit) => visit.id === "10")?.prescriptions).toHaveLength(1);
    expect(result.visits.find((visit) => visit.id === "11")?.prescriptions).toHaveLength(0);
    expect(result.unlinked.map((item) => item.prescriptionId)).toEqual([4]);
  });

  it("never guesses when a registration matches more than one record", () => {
    const result = groupVisitArchive([
      { medicalRecordId: 10, registrationId: 110 },
      { medicalRecordId: 11, registrationId: 110 },
    ], [{ prescriptionId: 5, registrationId: 110 }]);
    expect(result.unlinked).toHaveLength(1);
  });
});
