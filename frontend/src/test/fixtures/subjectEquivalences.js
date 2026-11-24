const actor = (overrides = {}) => ({ personId: 21, name: 'Marta Gómez', ...overrides });

export const subjectEquivalence = (overrides = {}) => ({
  id: 301,
  subjectId: 11,
  subjectCode: 'MAT-101',
  subjectName: 'Matemática I',
  credits: 6,
  inCurrentPlan: true,
  originInstitution: 'Universidad Nacional de Cuyo',
  originSubjectName: 'Análisis Matemático I',
  originScore: 8,
  resolutionNumber: 'RES-2026-114',
  recognizedOn: '2026-09-15',
  reason: 'Equivalencia por contenidos y carga horaria compatibles.',
  document: { id: 51, name: 'ANALITICO_SECUNDARIO' },
  status: 'ACTIVE',
  recordedBy: actor(),
  recordedAt: '2026-09-15T10:30:00',
  voidedBy: null,
  voidedAt: null,
  voidReason: null,
  ...overrides,
});

export const voidedSubjectEquivalence = (overrides = {}) =>
  subjectEquivalence({
    id: 303,
    subjectId: 13,
    subjectCode: 'FIS-103',
    subjectName: 'Física I',
    credits: 4,
    originSubjectName: 'Física General',
    originScore: 7.5,
    resolutionNumber: 'RES-2026-090',
    document: null,
    status: 'VOIDED',
    voidedBy: actor({ personId: 22, name: 'Raúl Díaz' }),
    voidedAt: '2026-10-02T14:00:00',
    voidReason: 'La resolución fue cargada contra la materia equivocada.',
    ...overrides,
  });

export const outOfPlanSubjectEquivalence = (overrides = {}) =>
  subjectEquivalence({
    id: 304,
    subjectId: 99,
    subjectCode: null,
    subjectName: null,
    credits: null,
    inCurrentPlan: false,
    originSubjectName: 'Introducción a la Programación',
    originScore: null,
    resolutionNumber: 'RES-2026-120',
    document: null,
    ...overrides,
  });

export const activeSubjectEquivalences = () => [
  subjectEquivalence(),
  subjectEquivalence({
    id: 302,
    subjectId: 12,
    subjectCode: 'LEN-102',
    subjectName: 'Lengua y Comunicación',
    credits: 4,
    originSubjectName: 'Taller de Lectura y Escritura',
    originScore: 9.5,
    resolutionNumber: 'RES-2026-115',
    recognizedOn: '2026-09-20',
    document: null,
  }),
];

export const ownSubjectEquivalence = (equivalence = subjectEquivalence()) => {
  const {
    reason: _reason,
    recordedBy: _recordedBy,
    recordedAt: _recordedAt,
    voidedBy: _voidedBy,
    voidedAt: _voidedAt,
    voidReason: _voidReason,
    ...own
  } = equivalence;
  return own;
};
