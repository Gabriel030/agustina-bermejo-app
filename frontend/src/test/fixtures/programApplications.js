export function reviewApplication(overrides = {}) {
  return {
    id: 51,
    studentId: 7,
    programId: 3,
    programName: 'Tecnicatura en Administración',
    programCohortId: 5,
    programCohortName: 'FIRST 2026',
    status: 'PENDING',
    requestedAt: '2026-11-05T10:00:00',
    decidedAt: null,
    decidedByName: null,
    rejectionReason: null,
    admissionInvoice: null,
    programEnrollmentId: 0,
    studentFullName: 'Valentina Suárez',
    studentEmail: 'valentina@correo.com',
    studentDni: '41234567',
    documentation: {
      complete: true,
      missingTypes: [],
      documents: [
        { id: 11, type: 'DNI_FRENTE', uploadedAt: '2026-11-04T09:00:00' },
        { id: 12, type: 'DNI_DORSO', uploadedAt: '2026-11-04T09:01:00' },
        { id: 13, type: 'ANALITICO_SECUNDARIO', uploadedAt: '2026-11-04T09:02:00' },
      ],
    },
    ...overrides,
  };
}

export function approvalCheck(overrides = {}) {
  return {
    applicationId: 51,
    canApprove: true,
    documentation: { complete: true, missingTypes: [], documents: [] },
    debt: { blocksApproval: false, violationCodes: [] },
    ...overrides,
  };
}
