import { z } from 'zod';

const optionalText = (max) => z.string().max(max, `Máximo ${max} caracteres`);

export const studentPersonalDataSchema = z.object({
  firstName: z.string().trim().min(1, 'Requerido').max(100, 'Máximo 100 caracteres'),
  lastName: z.string().trim().min(1, 'Requerido').max(100, 'Máximo 100 caracteres'),
  documentType: z.enum(['DNI', 'PASSPORT', 'OTHER']),
  documentNumber: z.string().trim().min(1, 'Requerido').max(30, 'Máximo 30 caracteres'),
  birthDate: z.string(),
  sex: optionalText(20),
  nationality: optionalText(100),
  phone: optionalText(50),
  email: z.string().trim().min(1, 'Requerido').email('Email inválido'),
});

export function studentToFormValues(legajo) {
  return {
    firstName: legajo.nombre ?? '',
    lastName: legajo.apellido ?? '',
    documentType: legajo.documentType ?? 'DNI',
    documentNumber: legajo.dni ?? '',
    birthDate: legajo.birthDate ?? '',
    sex: legajo.sexo ?? '',
    nationality: legajo.nacionalidad ?? '',
    phone: legajo.telefono ?? '',
    email: legajo.email ?? '',
  };
}

export function sensitiveChanges(legajo, values) {
  const emailChanged = values.email.trim().toLowerCase() !== (legajo.email ?? '').trim().toLowerCase();
  const documentChanged = values.documentNumber.trim() !== (legajo.dni ?? '').trim();
  return { emailChanged, documentChanged };
}

export function toUpdateStudentBody(values) {
  return {
    firstName: values.firstName.trim(),
    lastName: values.lastName.trim(),
    documentType: values.documentType,
    documentNumber: values.documentNumber.trim(),
    birthDate: values.birthDate || undefined,
    sex: values.sex || undefined,
    nationality: values.nationality.trim() || undefined,
    phone: values.phone.trim() || undefined,
    email: values.email.trim(),
  };
}
