import { Check, Clock, X, Minus, Paperclip } from 'lucide-react';

export const MARKABLE_STATUSES = [
  { value: 'PRESENT', label: 'Presente', icon: Check, rowClass: 'bg-success-bg', activeClass: 'border-success bg-success text-on-accent hover:bg-success hover:text-on-accent disabled:opacity-100', tone: 'success' },
  { value: 'LATE', label: 'Tarde', icon: Clock, rowClass: 'bg-warning-bg', activeClass: 'border-warning bg-warning-bg text-warning-ink ring-2 ring-warning hover:bg-warning-bg hover:text-warning-ink disabled:opacity-100', tone: 'warning' },
  { value: 'ABSENT', label: 'Ausente', icon: X, rowClass: 'bg-danger-bg', activeClass: 'border-danger bg-danger text-on-accent hover:bg-danger hover:text-on-accent disabled:opacity-100', tone: 'danger' },
];

export const JUSTIFICATION_STATUSES = [
  { value: 'JUSTIFIED', label: 'Justificada', icon: Paperclip, rowClass: 'bg-accent-soft', tone: 'accent' },
  { value: 'JUSTIFICATION_PENDING', label: 'Justificación pendiente', icon: Paperclip, rowClass: 'bg-accent-soft', tone: 'accent' },
];

export const UNMARKED_STATUS = { label: 'Sin marcar', icon: Minus, rowClass: 'bg-surface', tone: 'neutral' };

export const STATUS_BY_VALUE = Object.fromEntries([...MARKABLE_STATUSES, ...JUSTIFICATION_STATUSES].map((status) => [status.value, status]));

export const EDITABLE_SAVED_STATUSES = ['PRESENT', 'ABSENT', 'LATE'];

const SAVE_ERROR_MESSAGES = {
  CLASS_ON_HOLIDAY: 'Esa fecha es feriado: no se puede tomar asistencia.',
  ATTENDANCE_ALREADY_RECORDED: 'Esa asistencia ya estaba guardada. Si la registró otro docente o ya pasó el día, pedí la modificación desde Historial.',
};

export function isOfflineError(err) {
  return err.name === 'NetworkError' || err.name === 'CircuitOpenError';
}

export function saveErrorMessage(err) {
  if (SAVE_ERROR_MESSAGES[err.code]) return SAVE_ERROR_MESSAGES[err.code];
  const retry = 'Tus cambios siguen acá: probá de nuevo.';
  if (err.name === 'NetworkError' || err.name === 'CircuitOpenError' || err.name === 'RateLimitedError') return `${err.message} ${retry}`;
  if (err.status === 400) return `Hay datos de la asistencia que no son válidos. Revisalos y probá de nuevo.`;
  if (err.status === 403) return 'No tenés permiso para tomar asistencia en esta comisión.';
  if (err.status === 404) return 'No encontramos la comisión o la fecha. Recargá la página; tus cambios quedan guardados en este dispositivo.';
  if (err.status === 409) return 'La asistencia cambió mientras la cargabas. Recargá la página; tus cambios quedan guardados en este dispositivo.';
  if (err.status >= 500) return `El servicio no está disponible por ahora. ${retry}`;
  return `No se pudo guardar la asistencia. ${retry}`;
}

export function justificationState(savedCell, currentStatus) {
  if (!savedCell || savedCell.status !== 'ABSENT' || currentStatus !== 'ABSENT') return null;
  return savedCell.justifiable ? 'abierta' : 'vencida';
}
