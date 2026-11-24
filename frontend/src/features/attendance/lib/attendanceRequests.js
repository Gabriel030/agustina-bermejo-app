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

const ATTENDANCE_PATH = '/docente/tomar-asistencia';

export function attendanceHref(params = {}) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value != null) query.set(key, String(value));
  });
  const text = query.toString();
  return text ? `${ATTENDANCE_PATH}?${text}` : ATTENDANCE_PATH;
}

export function sectionParamOf(section) {
  return section ? `${section.type}-${section.id}` : null;
}

export function sectionParamOfRequest(request, sections) {
  if (!request) return null;
  return sectionParamOf(
    (sections ?? []).find((candidate) => candidate.subjectName === request.subjectName && candidate.sectionName === request.sectionName),
  );
}

export function reRequestHref(request, sections) {
  return attendanceHref({
    tab: 'historial',
    seccion: sectionParamOfRequest(request, sections),
    fecha: request.classDate,
    pedido: request.id,
  });
}

export function requestHref(requestId, seccion) {
  return attendanceHref({ tab: 'pedidos', seccion, pedido: requestId });
}

export function ordersHref(seccion) {
  return attendanceHref({ tab: 'pedidos', seccion });
}

export function pendingRequestsLabel(count) {
  return count === 1 ? '1 pendiente' : `${count} pendientes`;
}
