import { formatDecimal } from '@/lib/formatNumber';

export const CLOSURE_RESULT = {
  PROMOTES: { label: 'Promociona', kpiLabel: 'Total de promociones', tone: 'success', order: 0, summaryKey: 'promote' },
  PROMOTION_CONDITIONAL: { label: 'Promoción condicionada', kpiLabel: 'Promoción condicionada (provisoria)', tone: 'warning', order: 1, summaryKey: 'conditional' },
  GOES_TO_FINAL: { label: 'Va a final', kpiLabel: 'Total que van a final', tone: 'accent', order: 2, summaryKey: 'goToFinal' },
  FREE: { label: 'Libre', kpiLabel: 'Libres', tone: 'danger', order: 3, summaryKey: 'free' },
  FAILED: { label: 'Desaprobado', kpiLabel: 'Desaprobados', tone: 'danger', order: 4, summaryKey: 'failed' },
  PENDING: { label: 'Pendiente', kpiLabel: 'Pendientes', tone: 'neutral', order: 5, summaryKey: 'pending' },
};

export const RESULT_FILTER_ID = 'resultFilter';
export const WITHDRAWN_FILTER_VALUE = 'WITHDRAWN';

export const CLOSURE_STATUS = {
  DRAFT_AVAILABLE: {
    label: 'Borrador',
    tone: 'warning',
    description: 'Calculado por el sistema con las notas publicadas. Todavía no entregaste la planilla.',
  },
  TEACHER_CONFIRMED: {
    label: 'Entregada por el docente',
    tone: 'accent',
    description: 'Entregaste la planilla. Falta la aprobación del Administrativo.',
  },
  CLOSED: {
    label: 'Cerrado',
    tone: 'success',
    description: 'El cierre está aprobado y no se puede modificar.',
  },
  REOPENED: {
    label: 'Reabierto',
    tone: 'warning',
    description: 'Dirección reabrió el cierre. Revisá la planilla y volvé a confirmarla.',
  },
};

export const PAYMENTS_STANDING = { OK: true, NOT_OK: false, NOT_VERIFIED: null };

export const ALERT_LABEL = {
  UNMET_PREREQUISITE: 'Correlativa pendiente: ver Administración',
  EXAM_WITHOUT_COURSEWORK: 'Parcial cargado sin TP aprobados',
};

export const BLOCKED_REASON_FALLBACK = 'No se puede confirmar el cierre en este momento.';

export const PREREQUISITE_STATUS = {
  APPROVED: { label: 'Aprobada', tone: 'success' },
  REGULAR: { label: 'Regular', tone: 'accent' },
  NOT_TAKEN: { label: 'No cursada', tone: 'danger' },
  WAIVED: { label: 'Exceptuada', tone: 'accent' },
};

export const REQUIREMENT_TYPE_LABEL = {
  APPROVED: 'aprobada',
  PASSED_COURSEWORK: 'regular o aprobada',
};

export function isWithdrawn(row) {
  return row.enrollmentStatus === 'WITHDRAWN';
}

export function studentFullName(row) {
  return `${row.lastname}, ${row.name}`;
}

export function effectiveScore(grade) {
  if (!grade || grade.published !== true) return null;
  return grade.retakeScore ?? grade.score ?? null;
}

export function findGrade(row, column) {
  return row.grades.find((grade) => grade.type === column.type && grade.sequenceNumber === column.sequenceNumber);
}

export function examColumns(sheet) {
  return sheet.columns.filter((column) => column.type === 'EXAM');
}

export function columnLabel(column) {
  return `${column.sequenceNumber}.° parcial`;
}

export function formatScore(value) {
  return value == null ? '—' : formatDecimal(value, { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}

export function formatAverage(value) {
  return value == null ? '—' : formatDecimal(value, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function unpublishedColumns(sheet) {
  return sheet.columns.filter((column) => column.published !== true);
}

export function blockedMessage(sheet) {
  const { canConfirm, blockedReason } = sheet.availability;
  if (canConfirm) return null;
  if (blockedReason === 'PUBLISHED_GRADES_INCOMPLETE') {
    const labels = unpublishedColumns(sheet).map(columnLabel);
    return labels.length > 0
      ? `No se puede confirmar hasta publicar: ${labels.join(', ')}.`
      : 'No se puede confirmar hasta publicar todas las evaluaciones.';
  }
  if (blockedReason === 'ALREADY_CONFIRMED') return 'Ya confirmaste el cierre.';
  if (blockedReason === 'ALREADY_CLOSED') return 'El cierre ya está cerrado.';
  return BLOCKED_REASON_FALLBACK;
}

export function kpiCards(summary) {
  return Object.entries(CLOSURE_RESULT)
    .sort(([, a], [, b]) => a.order - b.order)
    .map(([key, config]) => ({ key, label: config.kpiLabel, count: summary?.[config.summaryKey] ?? 0 }));
}

export function resultFilterValue(row) {
  return isWithdrawn(row) ? WITHDRAWN_FILTER_VALUE : row.suggestedResult;
}

export function resultFilterLabel(value) {
  return value === WITHDRAWN_FILTER_VALUE ? 'Baja' : (CLOSURE_RESULT[value]?.label ?? value);
}

export function activeResultFilter(columnFilters) {
  const entry = columnFilters.find((filter) => filter.id === RESULT_FILTER_ID);
  return Array.isArray(entry?.value) ? entry.value : null;
}

export function toggleResultKpi(columnFilters, key) {
  const current = activeResultFilter(columnFilters);
  const others = columnFilters.filter((filter) => filter.id !== RESULT_FILTER_ID);
  const isSame = current?.length === 1 && current[0] === key;
  return isSame ? others : [...others, { id: RESULT_FILTER_ID, value: [key] }];
}

export function yesNoFilterLabel(value) {
  if (value === 'YES') return 'Sí';
  if (value === 'NO') return 'No';
  return 'No verificado';
}

export function paymentsFilterValue(row) {
  const standing = PAYMENTS_STANDING[row.administrative?.payments];
  if (standing === true) return 'YES';
  if (standing === false) return 'NO';
  return 'UNVERIFIED';
}

export function documentsFilterValue(row) {
  return row.administrative?.documentsComplete === true ? 'YES' : 'NO';
}

export function activeStudents(sheet) {
  return sheet.students.filter((row) => !isWithdrawn(row));
}

export function withdrawnCount(sheet) {
  return sheet.students.length - activeStudents(sheet).length;
}
