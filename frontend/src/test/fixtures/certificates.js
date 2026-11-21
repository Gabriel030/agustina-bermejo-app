export const CERTIFICATE_CODE = '7QK3M9XH2VBN4T8RGJDA1B2C3X';
export const CERTIFICATE_CODE_PRINTED = '7QK3M-9XH2V-BN4T8-RGJDA-1B2C3X';

export function graduationCertificate(overrides = {}) {
  return {
    documentKind: 'GRADUATION',
    status: 'VALID',
    holderFullName: 'Pérez, Ana',
    maskedDocument: '*****678',
    titleKind: 'TECNICATURA',
    titleName: 'Tecnicatura Superior en Enfermería',
    graduationDate: '2026-07-15',
    issuedAt: '2026-07-20T10:00:00',
    expiresAt: '2036-07-20T23:59:59',
    revokedAt: '2026-11-02T08:30:00',
    registryNumber: '1284',
    hours: 1860,
    ...overrides,
  };
}

export function regularCertificate(overrides = {}) {
  return {
    documentKind: 'REGULAR',
    status: 'VALID',
    holderFullName: 'Pérez, Ana',
    maskedDocument: '*****678',
    titleKind: 'TECNICATURA',
    titleName: 'Tecnicatura Superior en Enfermería',
    graduationDate: '2026-07-15',
    issuedAt: '2026-11-06T09:15:00',
    expiresAt: '2026-12-06T23:59:59',
    revokedAt: '2026-11-02T08:30:00',
    registryNumber: '77',
    hours: 0,
    ...overrides,
  };
}

export function springPage(content, { page = 0, size = 25, totalElements = content.length } = {}) {
  const sort = { empty: true, sorted: false, unsorted: true };
  return {
    content,
    empty: content.length === 0,
    first: page === 0,
    last: (page + 1) * size >= totalElements,
    number: page,
    numberOfElements: content.length,
    pageable: { offset: page * size, pageNumber: page, pageSize: size, paged: true, sort, unpaged: false },
    size,
    sort,
    totalElements,
    totalPages: Math.max(1, Math.ceil(totalElements / size)),
  };
}

export function eligibleGraduate(overrides = {}) {
  return {
    enrollmentId: 11,
    kind: 'PROGRAM',
    studentId: 21,
    studentFullName: 'Gómez, Tomás',
    holderDocumentType: 'DNI',
    documentNumber: '41003221',
    titleName: 'Tecnicatura en Administración',
    graduationDate: '2026-11-12',
    status: 'PENDING',
    ...overrides,
  };
}

export function graduationCertificateSummary(overrides = {}) {
  return {
    id: 101,
    code: '7QK3M9XH2VBN4T8RGJDA1B2C3X',
    registryNumber: '0000412/2026',
    kind: 'PROGRAM',
    status: 'VALID',
    holderFullName: 'Pérez, Camila',
    holderDocumentNumber: '38512774',
    titleName: 'Tecnicatura en Administración',
    graduationDate: '2026-11-12',
    issuedAt: '2026-11-20T10:00:00',
    templateVersionNumber: 3,
    ...overrides,
  };
}

export function graduationCertificateDetail(overrides = {}) {
  return {
    id: 101,
    code: '7QK3M9XH2VBN4T8RGJDA1B2C3X',
    registryNumber: '0000412/2026',
    kind: 'PROGRAM',
    status: 'VALID',
    holderFullName: 'Pérez, Camila',
    holderDocumentType: 'DNI',
    holderDocumentNumber: '38512774',
    titleName: 'Tecnicatura en Administración',
    hours: 1280,
    graduationDate: '2026-11-12',
    issuedAt: '2026-11-20T10:00:00',
    verificationUrl: 'http://localhost:5173/verificar/7QK3M9XH2VBN4T8RGJDA1B2C3X',
    templateVersionNumber: 3,
    revokedAt: null,
    revocationReason: null,
    replacesCertificateId: null,
    replacedByCertificateId: null,
    ...overrides,
  };
}

export function graduationCertificateBatch(overrides = {}) {
  return {
    requested: 2,
    issued: 1,
    failed: 1,
    results: [
      {
        enrollmentId: 11,
        kind: 'PROGRAM',
        outcome: 'ISSUED',
        certificate: graduationCertificateSummary({ holderFullName: 'Gómez, Tomás' }),
        errorCode: null,
      },
      { enrollmentId: 12, kind: 'PROGRAM', outcome: 'FAILED', certificate: null, errorCode: 'GRADUATION_CERTIFICATE_BLOCKED_BY_DEBT' },
    ],
    ...overrides,
  };
}

export function ownGraduationCertificate(overrides = {}) {
  return {
    id: 101,
    registryNumber: '0000412/2026',
    kind: 'PROGRAM',
    status: 'VALID',
    titleName: 'Tecnicatura en Administración',
    graduationDate: '2026-11-12',
    issuedAt: '2026-11-20T10:00:00',
    revokedAt: null,
    downloadable: true,
    previewable: true,
    ...overrides,
  };
}

export function certificateTemplateContent(overrides = {}) {
  return {
    instituteName: 'Instituto Agustina Bermejo',
    logoImageId: 1,
    title: 'Certificado de aprobación',
    introText: 'Se certifica que',
    approvalText: 'ha aprobado la carrera',
    dictationText: 'dictada y evaluada por',
    qrLabel: 'Verificá la autenticidad escaneando el código',
    footerLegend: 'Certificado emitido por el Instituto Agustina Bermejo. Verificá su autenticidad escaneando el código QR.',
    firstSigner: { name: 'Dra. Lucía Fernández', role: 'Secretaria Legal', signatureImageId: 2 },
    secondSigner: { name: 'Lic. Marcos Ledesma', role: 'Director Académico', signatureImageId: 3 },
    signatory1Id: 1,
    signatory2Id: 2,
    ...overrides,
  };
}

export function certificateTemplateVersion(overrides = {}) {
  return {
    id: 1,
    versionNumber: 3,
    status: 'PUBLISHED',
    basedOnVersionNumber: 2,
    content: certificateTemplateContent(),
    updatedAt: '2026-11-18T09:00:00',
    publishedAt: '2026-11-18T09:05:00',
    publishedBy: 'Director Prueba',
    ...overrides,
  };
}

export function certificateTemplate(overrides = {}) {
  return {
    kind: 'PROGRAM',
    published: certificateTemplateVersion(),
    draft: null,
    hasUnpublishedChanges: false,
    ...overrides,
  };
}

export function certificateTemplateImage(overrides = {}) {
  return {
    id: 1,
    purpose: 'LOGO',
    contentType: 'image/png',
    sizeBytes: 48213,
    widthPixels: 600,
    heightPixels: 600,
    ...overrides,
  };
}
