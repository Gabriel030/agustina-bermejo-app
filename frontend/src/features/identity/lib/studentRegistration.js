import { RateLimitedError } from '@/api/client';

export const DOCUMENT_LABELS = {
  DNI_FRENTE: 'DNI frente',
  DNI_DORSO: 'DNI dorso',
  ANALITICO_SECUNDARIO: 'Analítico secundario',
};

export const REGISTRATION_STEPS = [
  { key: '0', label: 'Datos básicos' },
  { key: '1', label: 'Documentos' },
  { key: '2', label: 'Datos personales' },
  { key: '3', label: 'Revisar y confirmar' },
  { key: '4', label: 'Alta confirmada' },
];

const REGISTRATION_ERRORS = {
  EMAIL_ALREADY_EXISTS: { step: 0, field: 'email', message: 'Ya existe una persona registrada con ese email.' },
  DNI_ALREADY_EXISTS: { step: 2, field: 'dni', message: 'Ya existe una persona registrada con ese documento.' },
  PROSPECT_ALREADY_CONFIRMED: { message: 'Este alumno ya fue confirmado.' },
};

export function describeRegistrationError(err, fallback) {
  const known = REGISTRATION_ERRORS[err?.code];
  if (known) return known;
  if (err instanceof RateLimitedError) return { message: err.message };
  if (err?.status === 403) return { message: 'No tenés permiso para dar de alta alumnos.' };
  if (err?.status === 400) return { message: 'El servidor rechazó los datos. Revisá que estén completos y sean válidos.' };
  return { message: fallback };
}

export function missingDocumentLabels(missingDocuments) {
  return (missingDocuments ?? []).map((type) => DOCUMENT_LABELS[type] ?? type);
}

export function missingDocumentsSummary(missingDocuments) {
  const labels = missingDocumentLabels(missingDocuments);
  return labels.length === 0 ? 'Documentación completa' : `Falta: ${labels.join(', ')}`;
}

export function buildProspectDraft(values) {
  return {
    documentType: values.documentType,
    dni: values.dni || null,
    street: values.street || null,
    number: values.number || null,
    floor: values.floor || null,
    city: values.city || null,
    state: values.state || null,
    postalCode: values.postalCode || null,
    country: values.country || null,
  };
}
