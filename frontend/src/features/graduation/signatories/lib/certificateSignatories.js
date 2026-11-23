import { DOMAIN_ERROR_MESSAGES } from '@/api/domainErrorMessages';

export const SIGNATURE_MAX_BYTES = 2 * 1024 * 1024;
export const NAME_MAX_LENGTH = 80;
export const ROLE_MAX_LENGTH = 80;
export const OTHER_ROLE = 'Otro';
export const ROLE_SUGGESTIONS = ['Secretaria Legal', 'Director Académico', OTHER_ROLE];

export const DOCUMENT_TYPE_OPTIONS = [
  { value: 'GRADUATION_APPROVAL', label: 'Certificado de egreso' },
  { value: 'REGULAR_STUDENT_CERTIFICATE', label: 'Constancia de alumno regular' },
  { value: 'APPROVED_SUBJECTS_CERTIFICATE', label: 'Constancia de materias aprobadas' },
  { value: 'EXAM_REGISTRATION_CERTIFICATE', label: 'Constancia de inscripción a examen' },
  { value: 'EXAM_CERTIFICATE', label: 'Constancia de examen' },
];

const TEMPLATE_STATUS_LABEL = { PUBLISHED: 'Publicada', DRAFT: 'Borrador' };

export function documentTypeLabelOf(value) {
  return DOCUMENT_TYPE_OPTIONS.find((option) => option.value === value)?.label ?? value;
}

export function templateUsageLabelOf(usage) {
  return `${usage.templateName} · versión ${usage.versionNumber} (${TEMPLATE_STATUS_LABEL[usage.status] ?? usage.status})`;
}

export function activityOf(signatory) {
  return signatory.active ? { label: 'Activo', tone: 'success' } : { label: 'Inactivo', tone: 'neutral' };
}

export function emptyDraft() {
  return { name: '', role: '', allowedDocumentTypes: [] };
}

export function draftOf(signatory) {
  return {
    name: signatory.name,
    role: signatory.role,
    allowedDocumentTypes: [...signatory.allowedDocumentTypes],
  };
}

export function toggleDocumentType(types, value, checked) {
  const without = types.filter((type) => type !== value);
  return checked ? [...without, value] : without;
}

function sameTypes(left, right) {
  return left.length === right.length && left.every((type) => right.includes(type));
}

export function isDraftChanged(draft, original) {
  return (
    draft.name !== original.name ||
    draft.role !== original.role ||
    !sameTypes(draft.allowedDocumentTypes, original.allowedDocumentTypes)
  );
}

export function isDraftComplete(draft) {
  return draft.name.trim().length > 0 && draft.role.trim().length > 0;
}

export function saveBodyOf(draft) {
  return {
    name: draft.name.trim(),
    role: draft.role.trim(),
    allowedDocumentTypes: DOCUMENT_TYPE_OPTIONS.map((option) => option.value).filter((value) =>
      draft.allowedDocumentTypes.includes(value),
    ),
  };
}

export function lostTemplateUsages(signatory, draft) {
  return signatory.usedInTemplates.filter((usage) => !draft.allowedDocumentTypes.includes(usage.documentType));
}

export function signatureProblemOf(file) {
  const isPng = file.type === 'image/png' && /\.png$/i.test(file.name);
  if (!isPng || file.size > SIGNATURE_MAX_BYTES) return DOMAIN_ERROR_MESSAGES.SIGNATORY_SIGNATURE_INVALID;
  return null;
}

export function deactivationDescriptionOf(signatory) {
  if (signatory.usedInTemplates.length === 0) {
    return 'No está elegido en ninguna plantilla. Deja de aparecer en los selectores y podés activarlo de nuevo cuando quieras.';
  }
  return 'Está elegido en plantillas. Al desactivarlo, esas plantillas quedan sin este firmante hasta que elijas otro. Los documentos ya emitidos no cambian.';
}
