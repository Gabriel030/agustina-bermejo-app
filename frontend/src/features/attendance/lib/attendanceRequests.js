export const STATUS_LABEL = {
  PRESENT: 'Presente',
  LATE: 'Tarde',
  ABSENT: 'Ausente',
  JUSTIFICATION_PENDING: 'Justificación pendiente',
  JUSTIFIED: 'Justificada',
};

export const REQUEST_STATUS = {
  PENDING: { label: 'Pendiente', tone: 'warning' },
  APPROVED: { label: 'Aprobada', tone: 'success' },
  REJECTED: { label: 'Rechazada', tone: 'danger' },
};

export const NO_REJECTION_REASON_TEXT = 'Se rechazó sin motivo registrado';

export function pendingRequestCount(requests) {
  return (requests ?? []).filter((request) => request.status === 'PENDING').length;
}

export function findPendingRequestForAttendance(requests, attendanceId) {
  if (attendanceId == null) return null;
  return (requests ?? []).find((request) => request.status === 'PENDING' && request.attendanceId === attendanceId) ?? null;
}

export function sectionLabelOf(request) {
  return `${request.subjectName} · ${request.sectionName}`;
}

export function reRequestHref(request, sections) {
  const section = (sections ?? []).find(
    (candidate) => candidate.subjectName === request.subjectName && candidate.sectionName === request.sectionName,
  );
  const params = new URLSearchParams({ tab: 'historial' });
  if (section) params.set('seccion', `${section.type}-${section.id}`);
  params.set('fecha', request.classDate);
  params.set('pedido', String(request.id));
  return `/docente/tomar-asistencia?${params.toString()}`;
}

export function requestHref(requestId) {
  return `/docente/tomar-asistencia?tab=pedidos&pedido=${requestId}`;
}
