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
  IN_COURSE: 'Cursando',
  REGULAR: 'Cursada regular',
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
    statuses: [],
    overdueDebt: false,
    staffRoles: [],
  },
  excludeMissingVariables: false,
  templateId: null,
  subject: '',
  bodyText: '',
};

export const SEGMENT_SPEC_VERSION = 1;

export function toSegmentSpec(criteria, catalogKeys) {
  const spec = [];
  criteria.programIds.forEach((id) => spec.push({ key: 'PROGRAM', value: String(id) }));
  if (criteria.subjectId) spec.push({ key: 'SUBJECT', value: String(criteria.subjectId) });
  criteria.statuses.forEach((status) => spec.push({ key: 'ENROLLMENT_STATUS', value: status }));
  if (criteria.overdueDebt && catalogKeys.includes('OVERDUE_DEBT')) spec.push({ key: 'OVERDUE_DEBT', value: '' });
  criteria.staffRoles.forEach((role) => spec.push({ key: 'STAFF_ROLE', value: role }));
  return { version: SEGMENT_SPEC_VERSION, quantifier: 'ALL', criteria: spec };
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

export function buildCreatePayload(state, segmentSpec) {
  const base = {
    name: state.name.trim(),
    channel: state.channel,
    category: 'INSTITUTIONAL',
    segmentSpec,
  };
  if (state.templateId != null) return { ...base, templateId: state.templateId };
  return { ...base, subject: state.subject, bodyText: state.bodyText };
}
