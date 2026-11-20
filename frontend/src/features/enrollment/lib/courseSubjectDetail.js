const ATTENDANCE_PRESENTATION = {
  PRESENT: { label: 'Presente', tone: 'success' },
  LATE: { label: 'Llegada tarde', tone: 'success' },
  ABSENT: { label: 'Ausente', tone: 'danger' },
  JUSTIFICATION_PENDING: { label: 'Justificación pendiente', tone: 'warning' },
  JUSTIFIED: { label: 'Justificada', tone: 'neutral' },
};

const GRADE_TYPE_LABEL = { EXAM: 'Parcial', TP: 'Trabajo práctico', FINAL: 'Final' };

const WARNING_RATIO = 0.7;

export function summarizeAttendance(attendance) {
  const records = attendance ?? [];
  return {
    classes: records.length,
    absent: records.filter((record) => record.status === 'ABSENT').length,
    justified: records.filter((record) => record.status === 'JUSTIFIED').length,
  };
}

export function describeAttendanceStatus(status) {
  return ATTENDANCE_PRESENTATION[status] ?? { label: status, tone: 'neutral' };
}

export function describeGradeType(type, sequenceNumber) {
  const label = GRADE_TYPE_LABEL[type] ?? 'Nota';
  return sequenceNumber ? `${label} ${sequenceNumber}` : label;
}

export function classifyAttendanceRisk(risk) {
  if (!risk?.calculable) return null;
  if (risk.libre) return 'danger';
  if (risk.absencePercent >= risk.maxAbsencePercent * WARNING_RATIO) return 'warning';
  return 'success';
}

export function formatPercent(value) {
  return `${Number(value).toLocaleString('es-AR', { maximumFractionDigits: 1 })}%`;
}

export function formatIsoDate(value) {
  if (!value) return null;
  const [year, month, day] = value.slice(0, 10).split('-');
  return `${day}/${month}/${year}`;
}

export function sortByClassDateDescending(attendance) {
  return [...(attendance ?? [])].sort((a, b) => b.classDate.localeCompare(a.classDate));
}
