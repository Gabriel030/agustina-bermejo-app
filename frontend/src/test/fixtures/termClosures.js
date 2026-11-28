export function pendingSectionClosure(overrides = {}) {
  return {
    sectionKind: 'PROGRAM',
    sectionId: 101,
    sectionLabel: 'Teorías Socioculturales · Comisión A',
    termEnd: '2026-07-10',
    daysAfterTermEnd: 9,
    graceOver: false,
    closureState: 'NOT_STARTED',
    hasPublishedGrades: false,
    ...overrides,
  };
}

export function pendingExamBoardClosure(overrides = {}) {
  return {
    examBoardId: 55,
    subjectName: 'Análisis Matemático',
    examDate: '2026-07-01T10:00:00',
    daysAfterExamDate: 18,
    graceOver: true,
    registeredCount: 4,
    withoutTribunal: false,
    ...overrides,
  };
}

export function pendingTermClosures(overrides = {}) {
  return {
    graceDays: 14,
    sections: [pendingSectionClosure(), pendingSectionClosure({ sectionId: 102, daysAfterTermEnd: 20, graceOver: true, closureState: 'TEACHER_CONFIRMED', hasPublishedGrades: true })],
    examBoards: [pendingExamBoardClosure()],
    ...overrides,
  };
}
