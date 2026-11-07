export function subjectFixture(overrides = {}) {
  return {
    id: 1,
    code: 'S001',
    name: 'Materia',
    status: 'ACTIVE',
    gradeMode: 'NUMERIC',
    description: null,
    rejectionReason: null,
    maxFinalExamAttempts: 3,
    weeklyHours: 4,
    weeklyHoursLab: null,
    ...overrides,
  };
}
