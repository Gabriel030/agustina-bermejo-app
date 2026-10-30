import { formatDate } from '@/lib/billing';

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

export const MANUAL_ISSUANCE_ERROR_MESSAGES = {
  MANUAL_ISSUANCE_OUTSIDE_CURRENT_MONTH: 'Solo se puede emitir a mano el mes en curso.',
  MANUAL_ISSUANCE_REQUIRES_SIMULATION: 'La simulación ya no es válida. Volvé a simular antes de emitir.',
  NIGHTLY_BILLING_NOT_COMPLETED: 'La corrida nocturna de hoy todavía no terminó. Esperá a que finalice para emitir.',
  MONTHLY_INVOICE_RUN_ALREADY_EXISTS: 'Este período ya tiene una corrida. Revisala en la pantalla.',
};

export const SIMULATION_LINE_LABELS = {
  ISSUED: 'Se emitiría',
  NOT_APPLICABLE: 'No aplica',
  ALREADY_ISSUED: 'Ya emitida',
  FAILED: 'Fallaría',
  RETRIES_EXHAUSTED: 'Fallaría',
};

export const SIMULATION_LINE_TONES = {
  ISSUED: 'success',
  NOT_APPLICABLE: 'neutral',
  ALREADY_ISSUED: 'neutral',
  FAILED: 'warning',
  RETRIES_EXHAUSTED: 'warning',
};

export function manualIssuanceErrorMessage(error) {
  return MANUAL_ISSUANCE_ERROR_MESSAGES[error?.code] ?? 'No pudimos emitir las cuotas. Intentá de nuevo.';
}

const ISSUABLE_STATUSES = ['COMPLETED', 'COMPLETED_WITH_FAILURES'];
const FAILING_RESULTS = ['FAILED', 'RETRIES_EXHAUSTED'];
const NIGHTLY_DONE_STATUSES = ['COMPLETED', 'COMPLETED_WITH_ERRORS'];

function sumCents(lines) {
  return lines.reduce((sum, line) => sum + Math.round(Number(line.total?.amount ?? 0) * 100), 0);
}

export function summarizeSimulation(simulation) {
  const lines = simulation?.lines ?? [];
  const issuable = lines.filter((line) => line.result === 'ISSUED');
  const dueDates = [...new Set(issuable.map((line) => line.dueDate).filter(Boolean))].sort();
  return {
    issuableCount: issuable.length,
    installmentCount: issuable.reduce((sum, line) => sum + (line.chargeCount ?? 0), 0),
    failingCount: lines.filter((line) => FAILING_RESULTS.includes(line.result)).length,
    total: { amount: (sumCents(issuable) / 100).toFixed(2), currency: issuable[0]?.total?.currency ?? 'ARS' },
    firstDueDate: dueDates[0] ?? null,
    lastDueDate: dueDates[dueDates.length - 1] ?? null,
    hasUndatedLines: issuable.some((line) => !line.dueDate),
  };
}

export function dueDateText(summary) {
  if (!summary.firstDueDate) return 'Sin vencimiento';
  if (summary.firstDueDate !== summary.lastDueDate) return `Entre ${formatDate(summary.firstDueDate)} y ${formatDate(summary.lastDueDate)}`;
  return summary.hasUndatedLines ? `${formatDate(summary.firstDueDate)} (algunas sin vencimiento)` : formatDate(summary.firstDueDate);
}

export function isSimulationValid(simulation, period, today) {
  if (!simulation || simulation.businessDate !== today) return false;
  if (simulation.period?.slice(0, 7) !== period) return false;
  if (!ISSUABLE_STATUSES.includes(simulation.status)) return false;
  return summarizeSimulation(simulation).issuableCount > 0;
}

export function isNightlyCompletedOn(runs, today) {
  return (runs ?? []).some((candidate) => candidate.businessDate === today && NIGHTLY_DONE_STATUSES.includes(candidate.status));
}

export function missedRunMessage(period, run) {
  if (!run) return `La emisión mensual de ${formatPeriod(period)} todavía no corrió.`;
  if (run.status === 'FAILED') return `La emisión mensual de ${formatPeriod(period)} falló${run.failureCode ? ` (${run.failureCode})` : ''}.`;
  return null;
}
