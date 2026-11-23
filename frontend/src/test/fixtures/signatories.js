export const SIGNATORY_DOCUMENT_TYPES = [
  'GRADUATION_APPROVAL',
  'REGULAR_STUDENT_CERTIFICATE',
  'APPROVED_SUBJECTS_CERTIFICATE',
  'EXAM_REGISTRATION_CERTIFICATE',
  'EXAM_CERTIFICATE',
];

export function signatoryTemplateUsage(overrides = {}) {
  return {
    documentType: 'GRADUATION_APPROVAL',
    templateName: 'Certificado de egreso de carrera',
    versionNumber: 3,
    status: 'PUBLISHED',
    ...overrides,
  };
}

export function signatorySummary(overrides = {}) {
  return {
    id: 1,
    name: 'Dra. Lucía Fernández',
    role: 'Secretaria Legal',
    active: true,
    hasSignature: true,
    allowedDocumentTypes: ['GRADUATION_APPROVAL', 'REGULAR_STUDENT_CERTIFICATE'],
    usedInTemplates: [signatoryTemplateUsage()],
    createdAt: '2026-11-18T09:00:00',
    updatedAt: '2026-11-19T15:30:00',
    ...overrides,
  };
}

export function signatoryDetail(overrides = {}) {
  const summary = signatorySummary(overrides);
  return { ...summary, signatureImageId: summary.hasSignature ? 10 + summary.id : null, ...overrides };
}

export function signatoryList() {
  return [
    signatorySummary(),
    signatorySummary({
      id: 2,
      name: 'Lic. Marcos Ledesma',
      role: 'Director Académico',
      allowedDocumentTypes: [...SIGNATORY_DOCUMENT_TYPES],
      usedInTemplates: [
        signatoryTemplateUsage(),
        signatoryTemplateUsage({ templateName: 'Certificado de egreso de carrera', versionNumber: 4, status: 'DRAFT' }),
      ],
    }),
    signatorySummary({
      id: 3,
      name: 'Prof. Elena Quiroga',
      role: 'Coordinadora de Exámenes',
      hasSignature: false,
      allowedDocumentTypes: ['EXAM_CERTIFICATE'],
      usedInTemplates: [],
      updatedAt: '2026-11-20T08:00:00',
    }),
    signatorySummary({
      id: 4,
      name: 'Mg. Ricardo Paz',
      role: 'Rector',
      active: false,
      allowedDocumentTypes: ['GRADUATION_APPROVAL', 'APPROVED_SUBJECTS_CERTIFICATE'],
      usedInTemplates: [],
      createdAt: '2026-10-01T10:00:00',
    }),
  ];
}
