import { CalendarCheck, Hourglass, Mail } from 'lucide-react';

export const SETTING_FIELDS = [
  {
    key: 'justificationDeadlineBusinessDays',
    label: 'Plazo para justificar inasistencias',
    unit: 'días hábiles',
    min: 0,
    max: 60,
    icon: CalendarCheck,
    group: 'academic',
    tip: 'Días hábiles que tiene el alumno para justificar una falta. Pasado el plazo, solo el preceptor puede justificarla como excepción, con motivo.',
  },
  {
    key: 'evaluationConfigDueDays',
    label: 'Plazo para configurar la evaluación',
    unit: 'días corridos',
    min: 1,
    max: 120,
    icon: Hourglass,
    group: 'academic',
    tip: 'Días corridos desde el inicio del período para que el docente complete la configuración de evaluación. Pasado el plazo figura como vencida en su panel.',
  },
  {
    key: 'announcementEmailEnabled',
    label: 'Avisos no urgentes por email',
    boolean: true,
    icon: Mail,
    group: 'notices',
    tip: 'Envía también por email los avisos no urgentes. Los urgentes siempre salen por email a quienes tienen el correo verificado.',
  },
];

export const SETTING_GROUPS = [
  { key: 'academic', label: 'Académico' },
  { key: 'notices', label: 'Avisos' },
];

export function toDraft(settings) {
  return {
    justificationDeadlineBusinessDays: String(settings.justificationDeadlineBusinessDays),
    evaluationConfigDueDays: String(settings.evaluationConfigDueDays),
    announcementEmailEnabled: settings.announcementEmailEnabled === true,
  };
}

export function validateField(field, value) {
  if (field.boolean) return null;
  const text = String(value).trim();
  if (text === '') return 'Ingresá un número';
  if (!/^\d+$/.test(text)) return 'Solo números enteros';
  const number = Number(text);
  if (number < field.min || number > field.max) return `Entre ${field.min} y ${field.max}`;
  return null;
}

export function isChanged(field, settings, draft) {
  if (field.boolean) return draft[field.key] !== settings[field.key];
  return Number(draft[field.key]) !== settings[field.key];
}

export function toPayload(draft) {
  return {
    justificationDeadlineBusinessDays: Number(draft.justificationDeadlineBusinessDays),
    evaluationConfigDueDays: Number(draft.evaluationConfigDueDays),
    announcementEmailEnabled: draft.announcementEmailEnabled,
  };
}

function formatValue(field, value) {
  if (field.boolean) return value ? 'Sí' : 'No';
  return `${value} ${field.unit}`;
}

function describeDifference(field, settings, draft) {
  if (!isChanged(field, settings, draft)) return 'Sin cambios';
  if (field.boolean) return draft[field.key] ? 'Se activa' : 'Se desactiva';
  const delta = Number(draft[field.key]) - settings[field.key];
  return `${delta > 0 ? '+' : '−'}${Math.abs(delta)} días`;
}

export function buildChangeRows(settings, draft) {
  return SETTING_FIELDS.map((field) => {
    const next = field.boolean ? draft[field.key] : Number(draft[field.key]);
    return {
      key: field.key,
      label: field.label,
      today: formatValue(field, settings[field.key]),
      next: formatValue(field, next),
      difference: describeDifference(field, settings, draft),
      unchanged: !isChanged(field, settings, draft),
    };
  });
}
