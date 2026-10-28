export const MAX_NOTICE_DAYS = 60;

export const MESSAGE_TYPES = {
  ISSUANCE_NOTICE: { milestone: 'ISSUANCE', sign: 0, label: 'Aviso de emisión' },
  AFTER_ISSUE_REMINDER: { milestone: 'ISSUANCE', sign: 1, label: 'Recordatorio tras la emisión' },
  BEFORE_DUE_REMINDER: { milestone: 'DUE_DATE', sign: -1, label: 'Recordatorio antes del vencimiento' },
  AFTER_DUE_REMINDER: { milestone: 'DUE_DATE', sign: 1, label: 'Aviso de cuota vencida' },
  BEFORE_SUSPENSION_REMINDER: { milestone: 'SUSPENSION', sign: -1, label: 'Aviso antes de la suspensión' },
};

export const MILESTONES = [
  { key: 'ISSUANCE', label: 'Emisión', phrase: 'de la emisión' },
  { key: 'DUE_DATE', label: 'Vencimiento', phrase: 'del vencimiento' },
  { key: 'SUSPENSION', label: 'Suspensión', phrase: 'de la suspensión' },
];

export const UNTIL_PHRASES = { ISSUANCE: 'la emisión', DUE_DATE: 'el vencimiento', SUSPENSION: 'la suspensión' };

export const CHANNEL_LABELS = { IN_APP: 'App', EMAIL: 'Mail' };

export const GROUP_LABELS = { PROGRAMS: 'Carreras', MONTHLY_COURSES: 'Cursos con pago mensual' };

export const SETTINGS_LABELS = {
  suspensionNoticeEnabled: 'Aviso antes de la suspensión',
  postWithdrawalSummaryMonths: 'Resumen tras una baja (meses)',
  emailWindowStart: 'Mails desde',
  emailWindowEnd: 'Mails hasta',
  dailyEmailCap: 'Tope diario de mails',
  contactListDaysOverdue: 'Para contactar: días de atraso',
  contactListOverdueInstallments: 'Para contactar: cuotas vencidas',
  dailyDigestEnabled: 'Resumen diario de avisos',
};

export const OVERRIDE_KEYS = [
  'suspensionNoticeEnabled',
  'postWithdrawalSummaryMonths',
  'emailWindowStart',
  'emailWindowEnd',
  'dailyEmailCap',
  'contactListDaysOverdue',
  'contactListOverdueInstallments',
];

export const SETTINGS_KEYS = [...OVERRIDE_KEYS, 'dailyDigestEnabled'];

export function noticeDays(notice) {
  return Math.abs(notice.offsetDays);
}

export function buildNotice({ messageType, days, channels, repetition = null, noticeKey }) {
  const type = MESSAGE_TYPES[messageType];
  return {
    noticeKey,
    messageType,
    milestone: type.milestone,
    offsetDays: type.sign === 0 ? 0 : type.sign * Number(days),
    channels,
    repetition,
  };
}

export function describeTiming(notice) {
  const type = MESSAGE_TYPES[notice.messageType];
  const phrase = MILESTONES.find((milestone) => milestone.key === notice.milestone).phrase;
  const days = noticeDays(notice);
  if (type.sign === 0 || days === 0) return `El día ${phrase}`;
  const unit = days === 1 ? 'día' : 'días';
  return `${days} ${unit} ${type.sign < 0 ? 'antes' : 'después'} ${phrase}`;
}

export function describeChannels(channels) {
  return [...channels].map((channel) => CHANNEL_LABELS[channel]).join(' y ');
}

export function describeRepetition(repetition) {
  if (!repetition?.everyDays) return null;
  return `Se repite cada ${repetition.everyDays} días hasta ${UNTIL_PHRASES[repetition.until] ?? ''}`.trim();
}

export function describeNotice(notice) {
  const parts = [describeTiming(notice), describeChannels(notice.channels), describeRepetition(notice.repetition)];
  return parts.filter(Boolean).join(' · ');
}

export function countDraftChanges(base, draft) {
  return diffSchedules(base, draft).length;
}

function sameNotice(a, b) {
  return JSON.stringify([a.messageType, a.offsetDays, [...a.channels].sort(), a.repetition?.everyDays ?? null, a.repetition?.until ?? null]) ===
    JSON.stringify([b.messageType, b.offsetDays, [...b.channels].sort(), b.repetition?.everyDays ?? null, b.repetition?.until ?? null]);
}

export function diffSettingValues(base, next, keys) {
  return keys
    .filter((key) => (base?.[key] ?? null) !== (next?.[key] ?? null))
    .map((key) => ({ key, label: SETTINGS_LABELS[key], today: base?.[key] ?? null, next: next?.[key] ?? null }));
}

export function diffSchedules(base, draft) {
  const rows = [];
  const baseByKey = new Map(base.notices.map((notice) => [notice.noticeKey, notice]));
  const draftKeys = new Set(draft.notices.map((notice) => notice.noticeKey));
  draft.notices.forEach((notice) => {
    const before = baseByKey.get(notice.noticeKey);
    if (!before) rows.push({ key: notice.noticeKey, kind: 'added', label: MESSAGE_TYPES[notice.messageType].label, today: null, next: notice });
    else if (!sameNotice(before, notice)) rows.push({ key: notice.noticeKey, kind: 'edited', label: MESSAGE_TYPES[notice.messageType].label, today: before, next: notice });
  });
  base.notices.forEach((notice) => {
    if (!draftKeys.has(notice.noticeKey)) rows.push({ key: notice.noticeKey, kind: 'removed', label: MESSAGE_TYPES[notice.messageType].label, today: notice, next: null });
  });
  diffSettingValues(base.sendingOverrides, draft.sendingOverrides, OVERRIDE_KEYS).forEach((row) =>
    rows.push({ ...row, kind: 'setting' }),
  );
  return rows;
}

export function localViolations(schedule) {
  const violations = [];
  const seen = new Set();
  schedule.notices.forEach((notice) => {
    const days = noticeDays(notice);
    const type = MESSAGE_TYPES[notice.messageType].label;
    if (!Number.isInteger(days) || days < 0 || days > MAX_NOTICE_DAYS) {
      violations.push(`${type}: los días tienen que estar entre 0 y ${MAX_NOTICE_DAYS}.`);
    }
    if (!notice.channels.length) violations.push(`${type}: elegí al menos un canal.`);
    const slot = `${notice.milestone}:${notice.offsetDays}`;
    if (seen.has(slot)) violations.push(`${type}: hay otro aviso el mismo día respecto del mismo hito.`);
    seen.add(slot);
  });
  if (schedule.notices.filter((notice) => notice.messageType === 'ISSUANCE_NOTICE').length > 1) {
    violations.push('El aviso de emisión es único.');
  }
  return violations;
}

export function inheritsAllOverrides(overrides) {
  return OVERRIDE_KEYS.every((key) => overrides?.[key] == null);
}

export function newNoticeKey() {
  return globalThis.crypto?.randomUUID?.() ?? `notice-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function tomorrowIso() {
  const date = new Date();
  date.setDate(date.getDate() + 1);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

export function pickEffective(versions) {
  return (versions ?? []).filter((version) => version.status === 'EFFECTIVE').sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom))[0] ?? null;
}

export function formatSettingValue(key, value) {
  if (value == null) return 'Hereda del general';
  if (typeof value === 'boolean') return value ? 'Sí' : 'No';
  return String(value);
}
