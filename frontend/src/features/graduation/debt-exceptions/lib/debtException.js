export const REASON_MIN_LENGTH = 20;
export const REASON_MAX_LENGTH = 500;
export const VALIDITY_MIN_DAYS = 1;
export const VALIDITY_MAX_DAYS = 90;
export const QUICK_VALIDITY_DAYS = [3, 7, 15];
export const DEFAULT_VALIDITY_DAYS = '7';
export const SEARCH_MIN_LENGTH = 2;

export const DOCUMENT_TYPES = [
  { value: 'REGULAR_STUDENT_CERTIFICATE', label: 'Constancia de alumno regular' },
  { value: 'EXAM_REGISTRATION_CERTIFICATE', label: 'Constancia de inscripción a examen' },
  { value: 'EXAM_CERTIFICATE', label: 'Constancia de examen' },
  { value: 'APPROVED_SUBJECTS_CERTIFICATE', label: 'Constancia de materias aprobadas' },
  { value: 'GRADUATION_CERTIFICATE', label: 'Certificado de egreso' },
];

export const DEFAULT_DOCUMENT_TYPE = DOCUMENT_TYPES[0].value;

export function documentTypeLabel(value) {
  return DOCUMENT_TYPES.find((type) => type.value === value)?.label ?? value;
}

export function sanitizeReason(text) {
  return text.replace(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/gu, ' ').slice(0, REASON_MAX_LENGTH);
}

export function reasonLength(text) {
  return text.trim().length;
}

export function isReasonValid(text) {
  return reasonLength(text) >= REASON_MIN_LENGTH;
}

export function parseValidityDays(text) {
  const value = String(text).trim();
  if (!/^\d+$/.test(value)) return null;
  const days = Number(value);
  return days >= VALIDITY_MIN_DAYS && days <= VALIDITY_MAX_DAYS ? days : null;
}

export function validityError(text) {
  if (String(text).trim() === '') return 'Ingresá cuántos días vale la constancia.';
  if (parseValidityDays(text) === null) return `Ingresá un número entero de ${VALIDITY_MIN_DAYS} a ${VALIDITY_MAX_DAYS} días.`;
  return null;
}

export function studentDisplayName(student) {
  return [student.apellido, student.nombre].filter(Boolean).join(', ');
}

export function canSubmit({ student, reason, validityDays }) {
  return Boolean(student) && isReasonValid(reason) && parseValidityDays(validityDays) !== null;
}

export function buildRequest({ student, documentType, reason, validityDays }) {
  return {
    studentId: student.id,
    documentType,
    reason: reason.trim(),
    validityDays: parseValidityDays(validityDays),
  };
}
