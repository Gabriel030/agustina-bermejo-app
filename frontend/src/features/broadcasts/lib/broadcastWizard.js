import { z } from 'zod';

export const CHANNEL_LABELS = { EMAIL: 'Correo', WHATSAPP_LINK: 'WhatsApp manual' };

export const EXCLUSION_REASON_LABELS = {
  NO_VERIFIED_EMAIL: 'Correo sin verificar',
  OPTED_OUT: 'Dado de baja',
  NO_CONSENT: 'Sin consentimiento',
  NO_VALID_PHONE: 'Teléfono inválido',
  NO_CHANNEL_ADDRESS: 'Sin dato de contacto',
  MISSING_TEMPLATE_VARIABLE: 'Falta un dato del mensaje',
};

export const ENROLLMENT_STATUS_LABELS = {
  STUDYING: 'Cursando',
  REGULAR: 'Cursada regular',
  FREE: 'Libre por inasistencias',
  GRADUATED: 'Egresado',
  WITHDRAWN: 'De baja',
};

export const STAFF_ROLE_LABELS = {
  DIRECTOR: 'Dirección',
  ADMINISTRATIVO: 'Administración',
  PRECEPTOR: 'Preceptoría',
  DOCENTE: 'Docencia',
};

export const TEMPLATE_VARIABLES = ['nombre', 'apellido', 'carrera', 'materia', 'comision', 'institucion', 'linkBaja'];

export const INITIAL_WIZARD_STATE = {
  step: 1,
  name: '',
  channel: 'EMAIL',
  criteria: {
    programIds: [],
    subjectId: null,
    sectionId: null,
    statuses: [],
    risk: false,
    riskThreshold: 75,
    overdueDebt: false,
    staffRoles: [],
  },
  excludeMissingVariables: false,
  templateId: null,
  subject: '',
  bodyText: '',
};

export function toSegmentSpec(criteria, catalogKeys) {
  const spec = [];
  if (criteria.programIds.length) spec.push({ key: 'PROGRAM', values: criteria.programIds });
  if (criteria.subjectId) spec.push({ key: 'SUBJECT', values: [criteria.subjectId] });
  if (criteria.sectionId) spec.push({ key: 'SECTION', values: [criteria.sectionId] });
  if (criteria.statuses.length) spec.push({ key: 'ENROLLMENT_STATUS', values: criteria.statuses });
  if (criteria.risk) spec.push({ key: 'ATTENDANCE_RISK', values: [criteria.riskThreshold] });
  if (criteria.overdueDebt && catalogKeys.includes('OVERDUE_DEBT')) spec.push({ key: 'OVERDUE_DEBT', values: [] });
  if (criteria.staffRoles.length) spec.push({ key: 'STAFF_ROLE', values: criteria.staffRoles });
  return { criteria: spec };
}

export function findUnknownVariables(text) {
  const used = [...text.matchAll(/\{\{\s*(\w+)\s*\}\}/g)].map((match) => match[1]);
  return [...new Set(used.filter((name) => !TEMPLATE_VARIABLES.includes(name)))];
}

export const messageSchema = z
  .object({
    channel: z.string(),
    subject: z.string().max(300, 'El asunto admite hasta 300 caracteres.'),
    bodyText: z.string().trim().min(1, 'Escribí el cuerpo del mensaje.').max(5000, 'El cuerpo admite hasta 5000 caracteres.'),
  })
  .superRefine((value, ctx) => {
    if (value.channel === 'EMAIL' && !value.subject.trim()) {
      ctx.addIssue({ code: 'custom', path: ['subject'], message: 'El asunto es obligatorio en los correos.' });
    }
    const unknown = findUnknownVariables(`${value.subject} ${value.bodyText}`);
    if (unknown.length) {
      ctx.addIssue({ code: 'custom', path: ['bodyText'], message: `La variable {{${unknown[0]}}} no existe.` });
    }
  });

export function validateMessage(state) {
  const result = messageSchema.safeParse(state);
  if (result.success) return {};
  return Object.fromEntries(result.error.issues.map((issue) => [issue.path[0], issue.message]));
}

const draftKey = (userId) => `broadcast-draft:${userId}`;

export function loadDraft(userId) {
  try {
    const raw = window.localStorage.getItem(draftKey(userId));
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function saveDraft(userId, state) {
  try {
    window.localStorage.setItem(draftKey(userId), JSON.stringify(state));
  } catch {
    return;
  }
}

export function clearDraft(userId) {
  try {
    window.localStorage.removeItem(draftKey(userId));
  } catch {
    return;
  }
}

export const DAILY_EMAIL_CAPACITY = 200;

export function estimateSendingDays(recipients) {
  return Math.max(1, Math.ceil(recipients / DAILY_EMAIL_CAPACITY));
}
