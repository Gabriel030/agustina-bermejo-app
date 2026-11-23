const verificationUrlOf = (code) => `/verificar/${code}`;

export const CERTIFICATE_DOCUMENT_TYPES = [
  'GRADUATION_CERTIFICATE',
  'REGULAR_STUDENT_CERTIFICATE',
  'APPROVED_SUBJECTS_CERTIFICATE',
  'EXAM_REGISTRATION_CERTIFICATE',
  'EXAM_CERTIFICATE',
];

export function publicDocumentVerification(overrides = {}) {
  return {
    documentType: 'REGULAR_STUDENT_CERTIFICATE',
    status: 'VALID',
    holderFullName: 'Ana Paz',
    maskedDocument: '***.***.222',
    issuedAt: '2026-11-18T09:00:00',
    expiresAt: '2026-12-18T09:00:00',
    revokedAt: null,
    replacedAt: null,
    registryNumber: null,
    ...overrides,
  };
}

export const PUBLIC_DOCUMENT_VERIFICATION_BY_TYPE = {
  GRADUATION_CERTIFICATE: () =>
    publicDocumentVerification({ documentType: 'GRADUATION_CERTIFICATE', expiresAt: null, registryNumber: 'EG-2026-000123' }),
  REGULAR_STUDENT_CERTIFICATE: () => publicDocumentVerification(),
  APPROVED_SUBJECTS_CERTIFICATE: () => publicDocumentVerification({ documentType: 'APPROVED_SUBJECTS_CERTIFICATE' }),
  EXAM_REGISTRATION_CERTIFICATE: () => publicDocumentVerification({ documentType: 'EXAM_REGISTRATION_CERTIFICATE' }),
  EXAM_CERTIFICATE: () => publicDocumentVerification({ documentType: 'EXAM_CERTIFICATE' }),
};

export function procedureEligibility(overrides = {}) {
  return {
    type: 'REGULAR_STUDENT_CERTIFICATE',
    eligible: true,
    enrollmentId: 7,
    summary: 'Tecnicatura en Enfermería, 2.º año',
    reasons: [],
    ...overrides,
  };
}

export function ineligibleProcedure(overrides = {}) {
  return procedureEligibility({
    eligible: false,
    summary: null,
    reasons: [
      {
        code: 'PENDING_PAYMENTS',
        message: 'Tenés pagos pendientes',
        nextStep: { kind: 'GO_TO_PAYMENTS', label: 'Ir a Mis pagos', path: '/mis-pagos' },
      },
      {
        code: 'NOT_REGULAR_STUDENT',
        message: 'Todavía no sos alumno regular',
        nextStep: { kind: 'CONTACT_ADMINISTRATION', label: 'Consultá en Administración', path: null },
      },
    ],
    ...overrides,
  });
}

export function approvedProcedure(overrides = {}) {
  return {
    documentType: 'REGULAR_STUDENT_CERTIFICATE',
    id: 31,
    title: 'Constancia de alumno regular',
    detail: 'Para presentar ante la obra social',
    issuedAt: '2026-11-18T09:00:00',
    expiresAt: '2026-12-18T09:00:00',
    status: 'VALID',
    origin: 'SELF_SERVICE',
    verifiable: true,
    ...overrides,
  };
}

export function approvedProcedureList() {
  return [
    approvedProcedure(),
    approvedProcedure({
      documentType: 'EXAM_CERTIFICATE',
      id: 12,
      title: 'Constancia de examen',
      detail: 'Anatomía II, 19/11/2026',
      origin: 'ISSUED_BY_TEACHER',
    }),
    approvedProcedure({
      documentType: 'APPROVED_SUBJECTS_CERTIFICATE',
      id: 8,
      title: 'Constancia de materias aprobadas',
      detail: null,
      status: 'EXPIRED',
      issuedAt: '2026-09-01T10:00:00',
      expiresAt: '2026-10-01T10:00:00',
    }),
  ];
}

export function approvedSubjectsCertificate(overrides = {}) {
  return {
    id: 8,
    code: '01JABCDEFGHJKMNPQRSTVWXYZ0',
    studentId: 3,
    studentName: 'Ana Paz',
    programName: 'Tecnicatura en Enfermería',
    enrollmentId: 7,
    status: 'VALID',
    issuedAt: '2026-11-21T10:00:00',
    expiresAt: '2026-12-21T10:00:00',
    averageGrade: 8.25,
    approvedSubjectsCount: 12,
    totalSubjectsCount: 30,
    approvedPercentage: 40,
    verificationUrl: verificationUrlOf('01JABCDEFGHJKMNPQRSTVWXYZ0'),
    ...overrides,
  };
}

export function examCertificate(overrides = {}) {
  return {
    id: 12,
    code: '01JABCDEFGHJKMNPQRSTVWXYZ1',
    registrationId: 55,
    examBoardId: 9,
    studentId: 3,
    studentName: 'Ana Paz',
    subjectName: 'Anatomía II',
    examDate: '2026-11-19',
    examBoardLabel: 'Mesa de noviembre',
    recipientType: null,
    recipientText: null,
    status: 'VALID',
    origin: 'ISSUED_BY_TEACHER',
    issuedAt: '2026-11-19T12:00:00',
    expiresAt: '2026-12-19T12:00:00',
    registryNumber: 'CE-2026-000045',
    verificationUrl: verificationUrlOf('01JABCDEFGHJKMNPQRSTVWXYZ1'),
    ...overrides,
  };
}

export function examCertificateCandidates() {
  return [
    { registrationId: 55, studentId: 3, studentName: 'Ana Paz', maskedDocument: '***.***.222', availability: 'AVAILABLE', certificateId: null },
    { registrationId: 56, studentId: 4, studentName: 'Luis Gómez', maskedDocument: '***.***.333', availability: 'ALREADY_ISSUED', certificateId: 12 },
    { registrationId: 57, studentId: 5, studentName: 'Marta Ríos', maskedDocument: '***.***.444', availability: 'ABSENT', certificateId: null },
  ];
}

export function examCertificateBatch(overrides = {}) {
  return {
    issuedCount: 1,
    skippedCount: 2,
    items: [
      { registrationId: 55, studentName: 'Ana Paz', outcome: 'ISSUED', certificateId: 13 },
      { registrationId: 56, studentName: 'Luis Gómez', outcome: 'ALREADY_ISSUED', certificateId: 12 },
      { registrationId: 57, studentName: 'Marta Ríos', outcome: 'ABSENT', certificateId: null },
    ],
    ...overrides,
  };
}

export function ownDocument(overrides = {}) {
  return {
    documentType: 'EXAM_REGISTRATION_CERTIFICATE',
    id: 70,
    code: '01JABCDEFGHJKMNPQRSTVWXYZ2',
    status: 'VALID',
    issuedAt: '2026-11-21T10:00:00',
    expiresAt: '2026-12-21T10:00:00',
    verificationUrl: verificationUrlOf('01JABCDEFGHJKMNPQRSTVWXYZ2'),
    ...overrides,
  };
}

export function changeSectionCandidate(overrides = {}) {
  return {
    sectionId: 20,
    name: 'Comisión A',
    capacity: 30,
    occupiedSeats: 30,
    availableSeats: 0,
    slots: [],
    selectable: false,
    blockReason: 'CURRENT_SECTION',
    blockDetail: 'Es tu comisión actual',
    conflictingSubject: '',
    ...overrides,
  };
}

export function changeSectionCandidateList() {
  return [
    changeSectionCandidate(),
    changeSectionCandidate({
      sectionId: 21,
      name: 'Comisión B',
      capacity: 30,
      occupiedSeats: 28,
      availableSeats: 2,
      selectable: true,
      blockReason: null,
      blockDetail: null,
      conflictingSubject: null,
    }),
    changeSectionCandidate({ sectionId: 22, name: 'Comisión C', blockReason: 'SECTION_FULL', blockDetail: 'No quedan lugares' }),
    changeSectionCandidate({ sectionId: 23, name: 'Comisión D', blockReason: 'SCHEDULE_OVERLAP', blockDetail: 'Se superpone con otra materia', conflictingSubject: 'Fisiología I' }),
  ];
}

export const RECIPIENT_TYPES = ['HEALTH_INSURANCE', 'EMPLOYER', 'STUDENT_TRANSPORT', 'OTHER'];

export function documentIssuanceSettings(overrides = {}) {
  return {
    examRegistrationAdvanceDays: 15,
    examAttendanceValidityDays: 30,
    approvedSubjectsValidityDays: 30,
    dailyIssuanceLimitPerType: 1,
    version: 0,
    ...overrides,
  };
}

export function debtException(overrides = {}) {
  return {
    id: 5,
    studentId: 3,
    studentName: 'Ana Paz',
    documentType: 'REGULAR_STUDENT_CERTIFICATE',
    status: 'ACTIVE',
    reason: 'Cuota abonada en efectivo pendiente de acreditar',
    issuedBy: 'Lucía Fernández',
    issuedAt: '2026-11-21T10:00:00',
    expiresAt: '2026-11-28T10:00:00',
    usedAt: null,
    documentId: null,
    verificationUrl: null,
    ...overrides,
  };
}

export function debtExceptionList() {
  return [
    debtException(),
    debtException({ id: 4, status: 'USED', usedAt: '2026-11-20T12:00:00', documentId: 31, verificationUrl: verificationUrlOf('01JABCDEFGHJKMNPQRSTVWXYZ3') }),
    debtException({ id: 3, status: 'EXPIRED', documentType: 'APPROVED_SUBJECTS_CERTIFICATE' }),
  ];
}
