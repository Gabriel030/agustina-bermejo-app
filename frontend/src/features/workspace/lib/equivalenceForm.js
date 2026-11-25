import { z } from 'zod';

export function equivalenceFieldId(name) {
  return `equivalence-field-${name}`;
}

export const REASON_MIN_LENGTH = 10;
export const REASON_MAX_LENGTH = 500;

export const EQUIVALENCE_FIELD_ORDER = [
  'programSubjectId',
  'originInstitution',
  'originSubjectName',
  'originScore',
  'recognizedOn',
  'resolutionNumber',
  'studentDocumentId',
  'reason',
];

export const EQUIVALENCE_FIELD_LABELS = {
  programSubjectId: 'Materia del plan',
  originInstitution: 'Institución de origen',
  originSubjectName: 'Materia de origen',
  originScore: 'Nota de origen',
  recognizedOn: 'Fecha de reconocimiento',
  resolutionNumber: 'Resolución',
  studentDocumentId: 'Documento del legajo',
  reason: 'Motivo',
};

export function todayIso(now = new Date()) {
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

export function parseScore(value) {
  const text = String(value ?? '').trim().replace(',', '.');
  if (text === '') return null;
  return /^\d+(\.\d{1,2})?$/.test(text) ? Number(text) : Number.NaN;
}

const reasonSchema = z
  .string()
  .trim()
  .min(REASON_MIN_LENGTH, `El motivo debe tener al menos ${REASON_MIN_LENGTH} caracteres.`)
  .max(REASON_MAX_LENGTH, `El motivo no puede superar los ${REASON_MAX_LENGTH} caracteres.`);

export const voidFormSchema = z.object({ reason: reasonSchema });

export const equivalenceFormSchema = z.object({
  programSubjectId: z.string().min(1, 'Elegí la materia del plan.'),
  originInstitution: z.string().trim().min(1, 'Indicá la institución de origen.').max(255, 'La institución no puede superar los 255 caracteres.'),
  originSubjectName: z.string().trim().min(1, 'Indicá la materia de origen.').max(255, 'La materia no puede superar los 255 caracteres.'),
  originScore: z.string().refine((value) => {
    const score = parseScore(value);
    return score === null || (Number.isFinite(score) && score >= 1 && score <= 10);
  }, 'La nota debe estar entre 1 y 10, con hasta 2 decimales.'),
  recognizedOn: z
    .string()
    .min(1, 'Indicá la fecha de reconocimiento.')
    .refine((value) => value === '' || value <= todayIso(), 'La fecha de reconocimiento no puede ser futura.'),
  resolutionNumber: z.string().trim().min(1, 'Indicá el número de resolución.').max(100, 'La resolución no puede superar los 100 caracteres.'),
  studentDocumentId: z.string(),
  reason: reasonSchema,
});

export function toRecordPayload(values, subject) {
  return {
    subjectId: subject.subjectId,
    originInstitution: values.originInstitution.trim(),
    originSubjectName: values.originSubjectName.trim(),
    originScore: parseScore(values.originScore),
    recognizedOn: values.recognizedOn,
    resolutionNumber: values.resolutionNumber.trim(),
    studentDocumentId: values.studentDocumentId ? Number(values.studentDocumentId) : null,
    reason: values.reason.trim(),
  };
}

export const EMPTY_EQUIVALENCE_VALUES = {
  programSubjectId: '',
  originInstitution: '',
  originSubjectName: '',
  originScore: '',
  recognizedOn: '',
  resolutionNumber: '',
  studentDocumentId: '',
  reason: '',
};
