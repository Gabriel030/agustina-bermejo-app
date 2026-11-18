export const BROADCAST_STATUS = {
  DRAFT: { label: 'Borrador', tone: 'neutral' },
  CONFIRMED: { label: 'Confirmado', tone: 'accent' },
  SENDING: { label: 'Enviando', tone: 'warning' },
  COMPLETED: { label: 'Completado', tone: 'success' },
  CANCELLED: { label: 'Cancelado', tone: 'danger' },
};

export const STATUS_FILTERS = ['DRAFT', 'CONFIRMED', 'SENDING', 'COMPLETED', 'CANCELLED'];

export const CANCELLABLE_STATUSES = ['DRAFT', 'CONFIRMED', 'SENDING'];

export const CHANNEL_LABEL = {
  EMAIL: 'Correo',
  WHATSAPP_LINK: 'WhatsApp (enlace)',
  WHATSAPP_API: 'WhatsApp',
};

export const CATEGORY_LABEL = {
  INSTITUTIONAL: 'Institucional',
  PROMOTIONAL: 'Promocional',
};

export const RECIPIENT_STATUS = {
  PENDING: { label: 'Pendiente', tone: 'neutral' },
  QUEUED: { label: 'En cola', tone: 'accent' },
  SENT: { label: 'Enviado', tone: 'success' },
  DONE: { label: 'Enviado', tone: 'success' },
  FAILED: { label: 'Falló', tone: 'danger' },
  INVALID_NUMBER: { label: 'Número inválido', tone: 'danger' },
  EXCLUDED: { label: 'Excluido', tone: 'warning' },
  SKIPPED: { label: 'Omitido', tone: 'warning' },
};

export const EXCLUSION_REASON_LABEL = {
  NO_VERIFIED_EMAIL: 'Sin correo verificado',
  OPTED_OUT: 'Se dio de baja',
  NO_CONSENT: 'Sin consentimiento',
  NO_VALID_PHONE: 'Sin teléfono válido',
  NO_CHANNEL_ADDRESS: 'Sin dirección para el canal',
  MISSING_TEMPLATE_VARIABLE: 'Falta un dato de la plantilla',
};

const SUM_GROUPS = {
  sent: ['SENT', 'DONE'],
  failed: ['FAILED', 'INVALID_NUMBER'],
  excluded: ['EXCLUDED', 'SKIPPED'],
  pending: ['PENDING', 'QUEUED'],
};

export function summarizeRecipientCounts(counts = {}) {
  const totals = Object.fromEntries(
    Object.entries(SUM_GROUPS).map(([group, keys]) => [group, keys.reduce((sum, key) => sum + (counts[key] ?? 0), 0)]),
  );
  const deliverable = totals.sent + totals.failed + totals.pending;
  const percent = deliverable === 0 ? 0 : Math.round((totals.sent / deliverable) * 100);
  return { ...totals, deliverable, percent };
}
