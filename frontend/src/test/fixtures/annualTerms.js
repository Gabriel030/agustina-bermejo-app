export const TERMS_TEXT_V1 =
  '## No devolución\nSi me doy de baja antes de que termine el año no se me devuelve lo pagado por adelantado.\n\n## Mi aceptación\nAcepto estos términos al pedir el año completo.';

export function annualTerms(overrides = {}) {
  return {
    versionNumber: 1,
    version: 'v1',
    text: TERMS_TEXT_V1,
    publishedAt: '2027-01-05T09:30:00',
    current: true,
    ...overrides,
  };
}

export function auditedAnnualTerms(overrides = {}) {
  return { ...annualTerms(), publishedByPersonId: 12, ...overrides };
}
