import { RateLimitedError } from '@/api/client';

export const DOCUMENT_LABELS = {
  DNI_FRENTE: 'DNI frente',
  DNI_DORSO: 'DNI dorso',
  ANALITICO_SECUNDARIO: 'Analítico secundario',
};

export const GUARDIANS_STEP = 3;
export const ADULT_AGE = 18;

export function registrationSteps(minor) {
  return [
    { key: '0', label: 'Datos básicos' },
    { key: '1', label: 'Documentos' },
    { key: '2', label: 'Datos personales' },
    { key: '3', label: minor ? 'Responsables' : 'Responsables (si es menor)' },
    { key: '4', label: 'Revisar y confirmar' },
    { key: '5', label: 'Alta confirmada' },
  ];
}

export const REGISTRATION_STEPS = registrationSteps(false);

export const SEX_OPTIONS = [
  { value: '', label: 'Seleccionar' },
  { value: 'F', label: 'Femenino' },
  { value: 'M', label: 'Masculino' },
  { value: 'X', label: 'No binario / otro' },
];

const relationshipOptions = (labels) => [{ value: '', label: 'Seleccionar' }, ...labels.map((label) => ({ value: label, label }))];

export const GUARDIAN_RELATIONSHIPS = relationshipOptions(['Madre', 'Padre', 'Tutor/a legal', 'Otro']);
export const EMERGENCY_RELATIONSHIPS = relationshipOptions(['Madre', 'Padre', 'Hermano/a', 'Abuelo/a', 'Otro']);

export function ageOn(birthDate, today = new Date()) {
  if (!birthDate) return null;
  const [year, month, day] = String(birthDate).split('-').map(Number);
  if (!year || !month || !day) return null;
  let age = today.getFullYear() - year;
  const beforeBirthday = today.getMonth() + 1 < month || (today.getMonth() + 1 === month && today.getDate() < day);
  if (beforeBirthday) age -= 1;
  return age;
}

export function isMinor(birthDate, today = new Date()) {
  const age = ageOn(birthDate, today);
  return age !== null && age >= 0 && age < ADULT_AGE;
}

export function todayIso(today = new Date()) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`;
}

export const GUARDIAN_DOCUMENT_PATTERN = /^\d{7,8}$/;

export function normalizeGuardianDocument(value) {
  return String(value ?? '').replace(/[.\s]/g, '');
}

const REGISTRATION_ERRORS = {
  EMAIL_ALREADY_EXISTS: { step: 0, field: 'email', message: 'Ya existe una persona registrada con ese email.' },
  DNI_ALREADY_EXISTS: { step: 2, field: 'dni', message: 'Ya existe una persona registrada con ese documento.' },
  BIRTH_DATE_REQUIRED: { step: 2, field: 'birthDate', message: 'Ingresá la fecha de nacimiento.' },
  GUARDIAN_REQUIRED_FOR_MINOR: {
    step: GUARDIANS_STEP,
    field: 'responsibles',
    message: 'Al ser menor de 18 años, completá los datos del tutor y del contacto de emergencia.',
  },
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

function buildGuardians(values) {
  if (!isMinor(values.birthDate)) return {};
  return {
    guardian: {
      fullName: values.guardianName?.trim() || null,
      documentNumber: normalizeGuardianDocument(values.guardianDni) || null,
      relationship: values.guardianRelationship || null,
      phone: values.guardianPhone?.trim() || null,
      email: values.guardianEmail?.trim() || null,
    },
    emergencyContact: {
      name: values.emergencyName?.trim() || null,
      relationship: values.emergencyRelationship || null,
      phone: values.emergencyPhone?.trim() || null,
    },
  };
}

export function buildProspectDraft(values) {
  return {
    birthDate: values.birthDate || null,
    sex: values.sex || null,
    nationality: values.nationality?.trim() || null,
    ...buildGuardians(values),
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
