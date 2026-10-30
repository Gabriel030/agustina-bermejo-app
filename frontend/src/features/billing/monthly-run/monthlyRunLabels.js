export const RUN_STATUS_LABELS = {
  RUNNING: 'En curso',
  COMPLETED: 'Completada',
  COMPLETED_WITH_FAILURES: 'Con fallas',
  FAILED: 'Fallida',
};

export const RUN_STATUS_TONES = {
  RUNNING: 'accent',
  COMPLETED: 'success',
  COMPLETED_WITH_FAILURES: 'warning',
  FAILED: 'danger',
};

export const RUN_TRIGGER_LABELS = {
  SCHEDULED: 'Programada',
  MANUAL: 'Manual',
};

export const ITEM_RESULT_LABELS = {
  ISSUED: 'Emitida',
  NOT_APPLICABLE: 'No aplica',
  ALREADY_ISSUED: 'Ya emitida',
  FAILED: 'Fallida',
  RETRIES_EXHAUSTED: 'Reintentos agotados',
};

export const ITEM_RESULT_TONES = {
  ISSUED: 'success',
  NOT_APPLICABLE: 'neutral',
  ALREADY_ISSUED: 'neutral',
  FAILED: 'warning',
  RETRIES_EXHAUSTED: 'danger',
};

export const ENROLLMENT_TYPE_LABELS = {
  PROGRAM: 'Carrera',
  COURSE: 'Curso',
};

const RETRYABLE_RESULTS = ['FAILED', 'RETRIES_EXHAUSTED'];

export function isRetryableResult(result) {
  return RETRYABLE_RESULTS.includes(result);
}

export function currentPeriod(now = new Date()) {
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

export function formatPeriod(period) {
  const [year, month] = period.split('-').map(Number);
  const name = new Intl.DateTimeFormat('es-AR', { month: 'long', timeZone: 'UTC' }).format(new Date(Date.UTC(year, month - 1, 1)));
  return `${name.charAt(0).toUpperCase()}${name.slice(1)} ${year}`;
}

export function periodOf(run) {
  return run.period.slice(0, 7);
}

export function findRealRun(runs) {
  return (runs ?? []).find((run) => run.mode === 'REAL') ?? null;
}

export function countProblemItems(run) {
  return (run?.counters?.failed ?? 0) + (run?.counters?.retriesExhausted ?? 0);
}

export function missedRunMessage(period, run) {
  if (!run) return `La emisión mensual de ${formatPeriod(period)} todavía no corrió.`;
  if (run.status === 'FAILED') return `La emisión mensual de ${formatPeriod(period)} falló${run.failureCode ? ` (${run.failureCode})` : ''}.`;
  return null;
}
