import { parsearFecha } from '@/lib/comisiones';

export const REASON_MIN_LENGTH = 20;
export const REASON_MAX_LENGTH = 500;

export const ORGANISM_OPTIONS = [
  { value: 'CONSUMER_DEFENSE', label: 'Defensa del Consumidor' },
  { value: 'EDUCATION_MINISTRY', label: 'Ministerio de Educación' },
  { value: 'COURT', label: 'Juzgado' },
  { value: 'OTHER', label: 'Otro organismo' },
];

export function organismLabelOf(kind) {
  return ORGANISM_OPTIONS.find((option) => option.value === kind)?.label ?? kind;
}

const EXCEPTION_STATUS = {
  PENDING: { label: 'Pendiente', tone: 'warning' },
  APPROVED: { label: 'Aprobada', tone: 'success' },
  REJECTED: { label: 'Rechazada', tone: 'danger' },
  WITHDRAWN: { label: 'Retirada', tone: 'neutral' },
  CONSUMED: { label: 'Usada', tone: 'neutral' },
  EXPIRED: { label: 'Vencida', tone: 'neutral' },
};

export function exceptionStatusOf(status) {
  return EXCEPTION_STATUS[status] ?? { label: status, tone: 'neutral' };
}

export function formatFullDate(iso) {
  if (!iso) return null;
  return parsearFecha(iso).toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

export function formatDayMonth(iso) {
  if (!iso) return null;
  return parsearFecha(iso).toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit' });
}

export function formatDateTime(iso) {
  if (!iso) return null;
  const time = parsearFecha(iso).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hour12: false });
  return `${formatFullDate(iso)} ${time}`;
}

export function overdueLabel(count) {
  if (count == null) return null;
  return count === 1 ? '1 cuota vencida' : `${count} cuotas vencidas`;
}

export function pendingLinkLabel(total) {
  if (total === 1) return 'Ver el pendiente';
  if (total > 1) return `Ver los ${total} pendientes`;
  return 'Ver todo';
}

export function reasonError(reason) {
  return reason.trim().length < REASON_MIN_LENGTH ? `El motivo debe tener al menos ${REASON_MIN_LENGTH} caracteres.` : '';
}

export function requestFormErrors({ organismKind, organismReference, reason }) {
  return {
    organismKind: organismKind ? '' : 'Elegí el tipo de organismo.',
    organismReference: organismReference.trim() ? '' : 'Cargá el número de expediente.',
    reason: reasonError(reason),
  };
}

export function hasFormErrors(errors) {
  return Object.values(errors).some(Boolean);
}

export function isOpenExceptionStatus(status) {
  return status === 'PENDING' || status === 'APPROVED';
}

export function requesterLabelOf(exception, currentUserId) {
  if (exception.requestedById === currentUserId) return 'Vos';
  return exception.createdByDirector ? 'el Director' : 'Administración';
}

export function indexByEnrollment(requests) {
  const index = new Map();
  for (const request of requests ?? []) {
    const key = `${request.enrollmentId}:${request.kind}`;
    if (!index.has(key)) index.set(key, request);
  }
  return index;
}

export function describeExceptionRow(exception, directory) {
  const known = directory.get(`${exception.enrollmentId}:${exception.kind}`);
  return {
    ...exception,
    requesterName: exception.createdByDirector ? 'El Director' : 'Administración',
    studentName: known?.studentName ?? `Alumno ${exception.studentId}`,
    programName: known?.programName ?? '—',
  };
}

export function candidateKey(request) {
  return `${request.enrollmentId}:${request.kind}`;
}

export function indebtedCandidates(requests) {
  const seen = new Set();
  const candidates = [];
  for (const request of requests ?? []) {
    if (!request.hasDebt || isOpenExceptionStatus(request.exceptionStatus) || seen.has(candidateKey(request))) continue;
    seen.add(candidateKey(request));
    candidates.push(request);
  }
  return candidates;
}

export function timelineEntries(exception) {
  const entries = [];
  entries.push(
    exception.createdByDirector
      ? { key: 'created', title: 'Creada y aprobada por el Director', when: formatDateTime(exception.requestedAt) }
      : { key: 'requested', title: `Pedida por ${exception.requesterName}`, when: formatDateTime(exception.requestedAt) },
  );
  const approvedStates = ['APPROVED', 'CONSUMED', 'EXPIRED'];
  if (!exception.createdByDirector && approvedStates.includes(exception.status)) {
    entries.push({ key: 'approved', title: 'Aprobada por el Director', when: formatDateTime(exception.decidedAt), detail: exception.decisionReason });
  }
  if (exception.status === 'REJECTED') {
    entries.push({ key: 'rejected', title: 'Rechazada por el Director', when: formatDateTime(exception.decidedAt), detail: exception.decisionReason });
  }
  if (approvedStates.includes(exception.status) && exception.validUntil) {
    entries.push({ key: 'validUntil', title: `Vence el ${formatFullDate(exception.validUntil)}` });
  }
  if (exception.status === 'CONSUMED') entries.push({ key: 'consumed', title: 'Usada para emitir la constancia' });
  if (exception.status === 'EXPIRED') entries.push({ key: 'expired', title: 'Venció sin usarse' });
  if (exception.status === 'WITHDRAWN') entries.push({ key: 'withdrawn', title: 'Retirada por quien la pidió', when: formatDateTime(exception.decidedAt) });
  return entries;
}
