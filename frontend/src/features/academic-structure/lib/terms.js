export const TERM_TYPE_LABELS = {
  ANNUAL: 'Anual',
  FIRST_SEMESTER: 'Primer cuatrimestre',
  SECOND_SEMESTER: 'Segundo cuatrimestre',
  INTENSIVE: 'Intensivo de verano',
};

export const TERM_TYPE_OPTIONS = Object.entries(TERM_TYPE_LABELS).map(([value, label]) => ({ value, label }));

export const TERM_STATUS_LABELS = { ACTIVE: 'Activo', INACTIVE: 'Inactivo' };

export const TERM_ACTIONS = {
  toggle: {
    title: (term) => (term.status === 'ACTIVE' ? 'Desactivar período' : 'Activar período'),
    description: (term) =>
      term.status === 'ACTIVE'
        ? 'El período deja de estar disponible para nuevas cursadas.'
        : 'El período vuelve a estar disponible.',
    path: (term) => (term.status === 'ACTIVE' ? 'deactivate' : 'activate'),
    success: (term) => (term.status === 'ACTIVE' ? 'Período desactivado.' : 'Período activado.'),
    destructive: (term) => term.status === 'ACTIVE',
  },
  open: {
    title: () => 'Abrir inscripción',
    description: () => 'La inscripción queda abierta aunque la ventana todavía no haya empezado.',
    path: () => 'enrollment/open',
    success: () => 'Inscripción abierta.',
  },
  close: {
    title: () => 'Cerrar inscripción',
    description: () => 'La inscripción queda cerrada aunque la ventana siga vigente.',
    path: () => 'enrollment/close',
    success: () => 'Inscripción cerrada.',
  },
  clear: {
    title: () => 'Quitar apertura manual',
    description: () => 'La inscripción vuelve a regirse solo por su ventana de fechas.',
    path: () => 'enrollment/clear-override',
    success: () => 'La inscripción vuelve a regirse por su ventana.',
  },
};

export function termTypeLabel(type) {
  return TERM_TYPE_LABELS[type] ?? type;
}

export function dateOnly(dateTime) {
  return dateTime ? dateTime.slice(0, 10) : '';
}

export function formatTermDate(dateTime) {
  const value = dateOnly(dateTime);
  if (!value) return '—';
  const [year, month, day] = value.split('-');
  return `${day}/${month}/${year}`;
}

export function toStartDateTime(date) {
  return date ? `${date}T00:00:00` : null;
}

export function toEndDateTime(date) {
  return date ? `${date}T23:59:59` : null;
}

function dayNumber(date) {
  const [year, month, day] = date.split('-').map(Number);
  return Date.UTC(year, month - 1, day) / 86400000;
}

export function daysBetween(fromDate, toDate) {
  return dayNumber(toDate) - dayNumber(fromDate);
}

export function formatDayDifference(days) {
  if (days === 0) return 'Sin cambios';
  const sign = days > 0 ? '+' : '−';
  const abs = Math.abs(days);
  return `${sign} ${abs} ${abs === 1 ? 'día' : 'días'}`;
}

export function emptyTermForm(year) {
  return {
    year: String(year),
    type: '',
    description: '',
    startDate: '',
    endDate: '',
    enrollmentStart: '',
    enrollmentEnd: '',
  };
}

export function termToForm(term) {
  return {
    year: String(term.year),
    type: term.type,
    description: term.description ?? '',
    startDate: dateOnly(term.startDate),
    endDate: dateOnly(term.endDate),
    enrollmentStart: dateOnly(term.enrollmentStart),
    enrollmentEnd: dateOnly(term.enrollmentEnd),
  };
}

export function validateTermForm(form, { editing }) {
  const errors = {};
  if (!editing && !form.year) errors.year = 'El año es obligatorio.';
  if (!editing && !form.type) errors.type = 'Elegí un tipo de período.';
  if (!form.description.trim()) errors.description = 'La descripción es obligatoria.';
  if (!form.startDate) errors.startDate = 'La fecha de inicio es obligatoria.';
  if (!form.endDate) errors.endDate = 'La fecha de fin es obligatoria.';
  if (form.startDate && form.endDate && form.endDate < form.startDate) {
    errors.endDate = 'El fin no puede ser anterior al inicio.';
  }
  if (!form.enrollmentStart) errors.enrollmentStart = 'El inicio de la inscripción es obligatorio.';
  if (!form.enrollmentEnd) errors.enrollmentEnd = 'El fin de la inscripción es obligatorio.';
  else if (form.enrollmentStart && form.enrollmentEnd < form.enrollmentStart) {
    errors.enrollmentEnd = 'La ventana de inscripción no es válida.';
  }
  return errors;
}

export function buildTermPayload(form, { editing }) {
  const payload = {
    description: form.description.trim(),
    startDate: toStartDateTime(form.startDate),
    endDate: toEndDateTime(form.endDate),
    enrollmentStart: toStartDateTime(form.enrollmentStart),
    enrollmentEnd: toEndDateTime(form.enrollmentEnd),
  };
  return editing ? payload : { ...payload, year: Number(form.year), type: form.type };
}

const DIFF_FIELDS = [
  { key: 'description', label: 'Descripción', date: false },
  { key: 'startDate', label: 'Inicio de cursada', date: true },
  { key: 'endDate', label: 'Fin de cursada', date: true },
  { key: 'enrollmentStart', label: 'Inicio de inscripción', date: true },
  { key: 'enrollmentEnd', label: 'Fin de inscripción', date: true },
];

function displayValue(field, value) {
  if (field.date) return value ? formatTermDate(value) : '—';
  return value || '—';
}

export function buildTermDiff(today, next) {
  return DIFF_FIELDS.filter((field) => (today[field.key] ?? '') !== (next[field.key] ?? '')).map((field) => {
    const from = today[field.key] ?? '';
    const to = next[field.key] ?? '';
    const difference = field.date && from && to ? formatDayDifference(daysBetween(from, to)) : 'Modificado';
    return {
      key: field.key,
      label: field.label,
      today: displayValue(field, from),
      next: displayValue(field, to),
      difference,
    };
  });
}

export function termErrorMessage(error, fallback) {
  if (error?.code === 'ACTIVE_TERM_ALREADY_EXISTS') {
    return 'Ya hay un período activo de este año y tipo. Desactivá el anterior o elegí otro tipo o año.';
  }
  if (error?.status === 409) {
    return 'No se pudo guardar: el cambio entra en conflicto con cursadas, comisiones o la configuración de cobro ya cargadas.';
  }
  if (error?.status === 400) return 'Los datos del período no son válidos. Revisá las fechas.';
  if (error?.status === 403) return 'No tenés permiso para gestionar períodos.';
  return fallback;
}
