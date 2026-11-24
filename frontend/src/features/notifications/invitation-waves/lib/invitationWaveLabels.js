import { AlertTriangle, Ban, Check, Clock, ListOrdered, Mail, Pause, Pencil, Play, X } from 'lucide-react';

export const WAVE_STATUS = {
  DRAFT: { label: 'Borrador', tone: 'neutral', icon: Pencil },
  RUNNING: { label: 'En curso', tone: 'accent', icon: Play },
  PAUSED: { label: 'Pausada', tone: 'warning', icon: Pause },
  COMPLETED: { label: 'Completada', tone: 'success', icon: Check },
  CANCELLED: { label: 'Cancelada', tone: 'danger', icon: X },
};

export const RECIPIENT_STATUS = {
  PENDING: { label: 'Pendiente', plural: 'Pendientes', tone: 'neutral', icon: Clock },
  QUEUED: { label: 'En cola', plural: 'En cola', tone: 'neutral', icon: ListOrdered },
  SENT: { label: 'Enviado', plural: 'Enviados', tone: 'accent', icon: Mail },
  FAILED: { label: 'Fallido', plural: 'Fallidos', tone: 'danger', icon: AlertTriangle },
  EXCLUDED: { label: 'Excluido', plural: 'Excluidos', tone: 'warning', icon: Ban },
  ACTIVATED: { label: 'Activó', plural: 'Activaron', tone: 'success', icon: Check },
};

export const RECIPIENT_STATUS_ORDER = ['PENDING', 'QUEUED', 'SENT', 'FAILED', 'EXCLUDED', 'ACTIVATED'];

export const EXCLUSION_LABELS = {
  NO_EMAIL: 'Sin email',
  ALREADY_ACTIVE: 'Ya activó su cuenta',
  NOT_ELIGIBLE: 'No elegible',
  IN_OTHER_WAVE: 'Ya está en otra oleada',
  WAVE_CANCELLED: 'Oleada cancelada',
};

export const DELIVERY_ERROR_LABELS = {
  PROVIDER_REJECTED: 'El proveedor de mail rechazó el envío',
  PROVIDER_UNAVAILABLE: 'El proveedor de mail no respondió',
  CIRCUIT_OPEN: 'Los envíos se frenaron un momento por fallas repetidas',
  BULKHEAD_FULL: 'El sistema estaba saturado',
  TOKEN_ISSUE_FAILED: 'No se pudo generar el enlace de activación',
  UNKNOWN: 'No se pudo entregar el mail',
};

export function recipientDetail(recipient) {
  if (recipient.status === 'EXCLUDED') return EXCLUSION_LABELS[recipient.exclusionReason] ?? 'Excluido';
  if (recipient.status === 'FAILED') {
    const reason = DELIVERY_ERROR_LABELS[recipient.lastErrorCode] ?? DELIVERY_ERROR_LABELS.UNKNOWN;
    return recipient.attempts > 1 ? `${reason} (${recipient.attempts} intentos)` : reason;
  }
  if (recipient.status === 'QUEUED') return 'Sale en la próxima tanda';
  if (recipient.status === 'ACTIVATED') return 'Activó su cuenta';
  if (recipient.status === 'SENT') return 'Esperando que active su cuenta';
  return '—';
}

export function personLabel(recipient) {
  return recipient.displayName || `Persona N° ${recipient.personId}`;
}

const MIN_ROWS_TO_INFER_MISSING_PERMISSION = 2;

export function lacksPersonalData(rows) {
  const named = rows.filter((row) => row.status !== 'EXCLUDED');
  return named.length >= MIN_ROWS_TO_INFER_MISSING_PERMISSION && named.every((row) => row.displayName == null);
}
