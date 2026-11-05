const IN_PROGRESS_STATUSES = new Set(['ENROLLED', 'IN_PROGRESS', 'REGULAR', 'WAITLISTED']);

const STATUS_PRESENTATION = {
  ENROLLED: { label: 'Cursando', tone: 'success' },
  IN_PROGRESS: { label: 'Cursando', tone: 'success' },
  REGULAR: { label: 'Cursando', tone: 'success' },
  WAITLISTED: { label: 'En lista de espera', tone: 'warning' },
  APPROVED: { label: 'Aprobado', tone: 'success' },
  FAILED: { label: 'Desaprobado', tone: 'danger' },
  WITHDRAWN: { label: 'Dado de baja', tone: 'neutral' },
  WAITLIST_EXPIRED: { label: 'Lista de espera vencida', tone: 'neutral' },
};

export function isCourseEnrollmentInProgress(enrollment) {
  return IN_PROGRESS_STATUSES.has(enrollment.status);
}

export function describeCourseEnrollmentStatus(status) {
  return STATUS_PRESENTATION[status] ?? { label: status, tone: 'neutral' };
}
