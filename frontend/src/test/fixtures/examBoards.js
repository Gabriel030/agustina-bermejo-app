export function examBoardFixture(overrides = {}) {
  return {
    id: 1,
    subjectId: 3,
    subjectName: 'Didáctica',
    courseSubjectId: 4,
    courseSubjectName: 'Didáctica',
    termId: 1,
    examDate: '2026-12-01T09:00:00',
    classroom: null,
    status: 'SCHEDULED',
    registrationStart: '2026-11-01T09:00:00',
    registrationEnd: '2026-11-20T09:00:00',
    tribunal: [],
    tribunalInstructorIds: [],
    canceledAt: null,
    cancelReason: null,
    rescheduledFrom: null,
    ...overrides,
  };
}
