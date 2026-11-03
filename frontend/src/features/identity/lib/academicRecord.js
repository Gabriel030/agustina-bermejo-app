export function enrollmentKey(kind, id) {
  return `${kind}-${id}`;
}

export function parseEnrollmentKey(key) {
  const [kind, id] = String(key).split('-');
  return { kind, id: Number(id) };
}

export function buildEnrollmentOptions(programEnrollments = [], courseEnrollments = []) {
  return [
    ...programEnrollments.map((enrollment) => ({
      key: enrollmentKey('program', enrollment.id),
      kind: 'program',
      id: enrollment.id,
      label: `${enrollment.programName} · Carrera`,
    })),
    ...courseEnrollments.map((enrollment) => ({
      key: enrollmentKey('course', enrollment.id),
      kind: 'course',
      id: enrollment.id,
      label: `${enrollment.courseName} · Curso`,
    })),
  ];
}

export const SECTION_STATUS = {
  APPROVED: { tone: 'success', label: 'Aprobada' },
  REGULAR: { tone: 'accent', label: 'Regular' },
  IN_PROGRESS: { tone: 'accent', label: 'Cursando' },
  FAILED: { tone: 'danger', label: 'Desaprobada' },
  WITHDRAWN: { tone: 'neutral', label: 'Dada de baja' },
  ENROLLED: { tone: 'neutral', label: 'Inscripta' },
  WAITLISTED: { tone: 'warning', label: 'En lista de espera' },
  WAITLIST_EXPIRED: { tone: 'neutral', label: 'Lista de espera vencida' },
};

export function describeSectionStatus(status) {
  return SECTION_STATUS[status] ?? { tone: 'neutral', label: status };
}

export const TERM_SHORT_LABEL = { FIRST: '1C', SECOND: '2C', ANNUAL: 'Anual' };

export function describeCurricularPeriod(year, term) {
  const termLabel = TERM_SHORT_LABEL[term];
  if (year == null) return termLabel ?? '—';
  return termLabel ? `${year}.º · ${termLabel}` : `${year}.º`;
}

export function formatDecimal(value) {
  return value == null ? '—' : String(value).replace('.', ',');
}

export function formatAttendancePercent(value) {
  return value == null ? '—' : `${Math.round(value)}%`;
}

export function formatGradeDate(isoDateTime) {
  if (!isoDateTime) return '—';
  const [year, month, day] = String(isoDateTime).slice(0, 10).split('-');
  return `${day}/${month}/${year}`;
}

export function gradeItemLabel(type) {
  if (type === 'EXAM') return 'Parcial';
  if (type === 'TP') return 'TP';
  return 'Final';
}

export function buildSubjectRows(sectionEnrollments = [], attendanceSummary = []) {
  const attendanceBySubject = new Map(attendanceSummary.map((entry) => [entry.subjectName, entry.attendancePercent]));
  return sectionEnrollments.map((section) => ({
    ...section,
    attendancePercent: attendanceBySubject.get(section.subjectName) ?? null,
  }));
}
