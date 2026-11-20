export const ADULT_AGE = 18;

export const LEGAL_DOCUMENT_LABEL = {
  TERMS: 'Términos y condiciones',
  PRIVACY: 'Política de privacidad',
};

export const LEGAL_DOCUMENT_PATH = {
  TERMS: '/legal/terminos',
  PRIVACY: '/legal/privacidad',
};

export const GUARDIAN_RESEND_FALLBACK_SECONDS = 600;

export function isMinorOn(birthDate, today = new Date()) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(birthDate ?? '')) return false;
  const [year, month, day] = birthDate.split('-').map(Number);
  let age = today.getFullYear() - year;
  const birthdayPassed = today.getMonth() + 1 > month || (today.getMonth() + 1 === month && today.getDate() >= day);
  if (!birthdayPassed) age -= 1;
  return age < ADULT_AGE;
}

export function formatLegalDate(isoDate) {
  if (!/^\d{4}-\d{2}-\d{2}/.test(isoDate ?? '')) return '';
  const [year, month, day] = isoDate.slice(0, 10).split('-').map(Number);
  return new Date(year, month - 1, day).toLocaleDateString('es-AR', { day: 'numeric', month: 'long', year: 'numeric' });
}

export function formatLegalDateTime(isoDateTime) {
  const date = new Date(isoDateTime);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleDateString('es-AR', { day: 'numeric', month: 'long', year: 'numeric' });
}
