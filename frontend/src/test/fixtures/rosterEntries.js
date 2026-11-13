export function absenceStandingFixture(overrides = {}) {
  return {
    classesHeld: 12,
    countableAbsences: 0,
    pendingJustificationAbsences: 0,
    absencePercent: 0,
    maxAbsencePercent: 25,
    allowedAbsences: 3,
    remainingAbsences: 3,
    exceedsAbsenceLimit: false,
    ...overrides,
  };
}

export function rosterEntryFixture(overrides = {}, standing = {}) {
  const absenceStanding = absenceStandingFixture(standing);
  return {
    enrollmentId: 1,
    studentId: 11,
    studentName: 'Acosta, Joaquín',
    dni: '40111222',
    absenceCount: absenceStanding.countableAbsences,
    atRisk: absenceStanding.exceedsAbsenceLimit,
    attendancePercent: 100,
    classesHeld: absenceStanding.classesHeld,
    maxAbsenceRate: absenceStanding.maxAbsencePercent,
    remainingAbsences: absenceStanding.remainingAbsences,
    absencePercent: absenceStanding.absencePercent,
    pendingJustificationAbsences: absenceStanding.pendingJustificationAbsences,
    absenceStanding,
    ...overrides,
  };
}
