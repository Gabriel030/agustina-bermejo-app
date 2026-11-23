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
    verificationUrl: 'http://localhost:5173/verificar/01JABCDEFGHJKMNPQRSTVWXYZ0',
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
    verificationUrl: 'http://localhost:5173/verificar/01JABCDEFGHJKMNPQRSTVWXYZ1',
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
    verificationUrl: 'http://localhost:5173/verificar/01JABCDEFGHJKMNPQRSTVWXYZ2',
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

export function regularCertificateRequest(overrides = {}) {
  return {
    id: 301,
    studentId: 41,
    studentName: 'Lucía Fernández',
    programName: 'Tecnicatura en Enfermería',
    kind: 'PROGRAM',
    enrollmentId: 77,
    channel: 'STUDENT_PORTAL',
    outcome: 'PENDING_STAFF',
    reasons: ['PAYMENT_STANDING'],
    requestedAt: '2026-11-20T09:30:00',
    certificateId: null,
    rejectionReason: null,
    totalDue: { amount: '45000.00', currency: 'ARS' },
    overdueInstallments: 2,
    hasDebt: true,
    exceptionId: null,
    exceptionStatus: null,
    exceptionValidUntil: null,
    ...overrides,
  };
}

export function regularCertificateRequestList() {
  return [
    regularCertificateRequest(),
    regularCertificateRequest({ id: 302, studentId: 42, studentName: 'Martín Gómez', enrollmentId: 78, totalDue: { amount: '0.00', currency: 'ARS' }, overdueInstallments: 0, hasDebt: false }),
    regularCertificateRequest({ id: 303, studentId: 43, studentName: 'Sofía Ruiz', enrollmentId: 79, totalDue: null, overdueInstallments: null, hasDebt: null }),
    regularCertificateRequest({ id: 304, studentId: 44, studentName: 'Tomás Acosta', enrollmentId: 80, exceptionId: 9, exceptionStatus: 'PENDING' }),
    regularCertificateRequest({ id: 305, studentId: 45, studentName: 'Valeria Núñez', enrollmentId: 81, exceptionId: 10, exceptionStatus: 'APPROVED', exceptionValidUntil: '2026-11-30T10:00:00' }),
  ];
}

export function certificateExceptionSummary(overrides = {}) {
  return { pending: 2, approved: 1, rejected: 3, withdrawn: 0, expired: 1, consumed: 4, ...overrides };
}

export function certificateException(overrides = {}) {
  return {
    id: 9,
    studentId: 44,
    kind: 'PROGRAM',
    enrollmentId: 80,
    organismKind: 'COURT',
    organismReference: 'Causa 77/2026',
    reason: 'El alumno presentó un reclamo formal ante Defensa del Consumidor',
    status: 'PENDING',
    requestedById: 5,
    requestedAt: '2026-11-21T10:00:00',
    decidedById: null,
    decidedAt: null,
    decisionReason: null,
    validUntil: null,
    createdByDirector: false,
    debtTotalAtRequest: { amount: '45000.00', currency: 'ARS' },
    overdueInstallmentsAtRequest: 2,
    ...overrides,
  };
}
