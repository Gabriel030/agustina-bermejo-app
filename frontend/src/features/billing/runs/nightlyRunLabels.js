export const RUN_STATUS_LABELS = {
  RUNNING: 'En curso',
  COMPLETED: 'Completada',
  COMPLETED_WITH_ERRORS: 'Con errores',
  FAILED: 'Fallida',
};

export const RUN_STATUS_TONES = {
  RUNNING: 'accent',
  COMPLETED: 'success',
  COMPLETED_WITH_ERRORS: 'warning',
  FAILED: 'danger',
};

export const STEP_STATUS_LABELS = {
  ...RUN_STATUS_LABELS,
  NOT_APPLICABLE: 'No aplica',
};

export const STEP_STATUS_TONES = {
  ...RUN_STATUS_TONES,
  NOT_APPLICABLE: 'neutral',
};

export const TRIGGER_LABELS = {
  SCHEDULED: 'Programada',
  MANUAL: 'Manual',
};

export const STEP_LABELS = {
  ACCOUNT_CONSISTENCY: 'Consistencia de cuentas',
  GATEWAY_SYNC: 'Sincronización con pasarelas',
  OVERDUE_DETECTION: 'Detección de facturas vencidas',
  LATE_FEE_ACCRUAL: 'Recargos por mora',
  LATE_FEE_WITHDRAWAL_CORRECTION: 'Corrección de recargos por baja',
  COLLECTIONS_SNAPSHOT: 'Foto diaria de cobranza',
  ENROLLMENT_BILLING_COMPLIANCE: 'Estado de pago de inscripciones',
  TERM_END_WITHDRAWAL: 'Bajas al cierre de ciclo',
  ACCOUNT_CREDIT_ALLOCATION: 'Imputación del saldo a favor',
};

export const NOT_APPLICABLE_REASONS = {
  GATEWAY_DISABLED: 'No aplica: pasarela desactivada',
  AUTO_APPLY_CREDIT_DISABLED: 'No aplica: imputación automática desactivada',
};

export const ITEM_STATUS_LABELS = {
  SKIPPED: 'Omitido',
  FAILED: 'Con error',
};

export const ITEM_STATUS_TONES = {
  SKIPPED: 'neutral',
  FAILED: 'danger',
};

export const SUBJECT_TYPE_LABELS = {
  INVOICE: 'Factura',
  LATE_FEE_CHARGE: 'Recargo',
  PROGRAM_ENROLLMENT: 'Inscripción a carrera',
  COURSE_ENROLLMENT: 'Inscripción a curso',
  CURRENT_ACCOUNT: 'Cuenta corriente',
};

export const HELD_ACCOUNT_CODE = 'INCONSISTENT_ACCOUNT';

export function stepLabel(step) {
  return STEP_LABELS[step] ?? step;
}

export function notApplicableReason(code) {
  return NOT_APPLICABLE_REASONS[code] ?? 'No aplica';
}

export function isNightWithErrors(run) {
  return run.status === 'COMPLETED_WITH_ERRORS' || run.status === 'FAILED';
}

export function countNightsWithErrors(runs) {
  return (runs ?? []).filter(isNightWithErrors).length;
}

export function formatRunDuration(totalSeconds) {
  if (totalSeconds == null) return '';
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return minutes > 0 ? `${minutes} min ${seconds} s` : `${seconds} s`;
}
