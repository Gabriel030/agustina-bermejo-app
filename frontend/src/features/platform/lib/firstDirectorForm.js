import { validateDocument } from '@/lib/documentType';
import { FIELD_LIMITS } from '@/features/platform/lib/firstDirectorErrors';

export const EMPTY_FORM = { institutionName: '', firstName: '', lastName: '', email: '', documentType: 'DNI', documentNumber: '', reason: '' };
export const REASON_MIN = 5;
export const REASON_MAX = 500;
export const FIELD_ORDER = ['institutionName', 'firstName', 'lastName', 'email', 'documentNumber', 'reason'];

const EMAIL_FORMAT = /^\S+@\S+\.\S+$/;

const FIELD_LABELS = {
  institutionName: 'Institución',
  firstName: 'Nombre',
  lastName: 'Apellido',
  email: 'Email',
  documentNumber: 'Documento',
  reason: 'Motivo',
};

function limitMessage(label, limit) {
  return `${label} admite hasta ${limit} caracteres`;
}

export function validateForm(form) {
  const errors = {};
  if (!form.institutionName.trim()) errors.institutionName = 'Ingresá el nombre de la institución';
  else if (form.institutionName.trim().length > FIELD_LIMITS.institutionName) errors.institutionName = limitMessage('El nombre de la institución', FIELD_LIMITS.institutionName);
  if (!form.firstName.trim()) errors.firstName = 'Ingresá el nombre';
  else if (form.firstName.trim().length > FIELD_LIMITS.firstName) errors.firstName = limitMessage('El nombre', FIELD_LIMITS.firstName);
  if (!form.lastName.trim()) errors.lastName = 'Ingresá el apellido';
  else if (form.lastName.trim().length > FIELD_LIMITS.lastName) errors.lastName = limitMessage('El apellido', FIELD_LIMITS.lastName);
  if (!EMAIL_FORMAT.test(form.email.trim())) errors.email = 'Ingresá un email válido';
  else if (form.email.trim().length > FIELD_LIMITS.email) errors.email = limitMessage('El email', FIELD_LIMITS.email);
  const documentError = form.documentNumber.trim() ? validateDocument(form.documentType, form.documentNumber) : 'Ingresá el documento';
  if (documentError) errors.documentNumber = documentError;
  else if (form.documentNumber.trim().length > FIELD_LIMITS.documentNumber) errors.documentNumber = limitMessage('El documento', FIELD_LIMITS.documentNumber);
  if (form.reason.trim().length < REASON_MIN) errors.reason = `El motivo necesita al menos ${REASON_MIN} caracteres`;
  else if (form.reason.length > REASON_MAX) errors.reason = `El motivo admite hasta ${REASON_MAX} caracteres`;
  return errors;
}

export function toRequest(form) {
  return {
    institutionName: form.institutionName.trim(),
    firstName: form.firstName.trim(),
    lastName: form.lastName.trim(),
    email: form.email.trim(),
    documentType: form.documentType,
    documentNumber: form.documentNumber.trim(),
    reason: form.reason.trim(),
  };
}

export function firstInvalidField(errors) {
  return FIELD_ORDER.find((field) => errors[field]) ?? null;
}

export function missingFieldLabels(errors) {
  return FIELD_ORDER.filter((field) => errors[field]).map((field) => FIELD_LABELS[field]);
}
