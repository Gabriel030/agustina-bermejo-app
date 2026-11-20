import { formatDecimal } from '@/lib/formatNumber';

const TIME_ZONE = 'America/Argentina/Buenos_Aires';
const BYTE_UNITS = ['B', 'KB', 'MB', 'GB', 'TB'];

const momentFormat = new Intl.DateTimeFormat('es-AR', {
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: TIME_ZONE,
});

export function formatBackupMoment(value) {
  if (!value) return '—';
  const parts = Object.fromEntries(momentFormat.formatToParts(new Date(value)).map((part) => [part.type, part.value]));
  return `${parts.day}/${parts.month} ${parts.hour}:${parts.minute}`;
}

export function formatHours(hours) {
  if (hours == null) return '—';
  return `${formatDecimal(hours, { maximumFractionDigits: 1 })} h`;
}

export function formatBytes(bytes) {
  if (bytes == null) return '—';
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < BYTE_UNITS.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${formatDecimal(value, { maximumFractionDigits: unit < 2 ? 0 : 1 })} ${BYTE_UNITS[unit]}`;
}

const UNKNOWN_PILL = { tone: 'neutral', label: 'Desconocido' };

export const TIER_LABELS = {
  INTRADAY: 'Del día',
  DAILY: 'Diaria',
  WEEKLY: 'Semanal',
  QUARTERLY: 'Trimestral',
  PRE_RELEASE: 'Previa a actualización',
};

export const VERIFICATION_PILLS = {
  VERIFIED: { tone: 'success', label: 'Verificada' },
  DAMAGED: { tone: 'danger', label: 'Dañada' },
  UNVERIFIED: { tone: 'warning', label: 'Sin verificar' },
};

export const ALERT_LEVEL_PILLS = {
  CRITICAL: { tone: 'danger', label: 'Crítica' },
  WARNING: { tone: 'warning', label: 'Aviso' },
};

export const RESTORE_RESULT_PILLS = {
  PASSED: { tone: 'success', label: 'Aprobada' },
  FAILED: { tone: 'danger', label: 'Fallida' },
};

export function describeBackupState(status) {
  const { state, summary, generatedAt } = status;
  const lastBackupAge = formatHours(summary.hoursSinceLastCompleteBackup);
  const possibleLoss = formatHours(summary.potentialLossHours);
  if (state === 'OK') {
    return { tone: 'success', lead: 'Todo en orden:', text: `el último backup terminó hace ${lastBackupAge}.`, needsSupport: false };
  }
  if (state === 'ATTENTION') {
    const text =
      summary.hoursSinceLastCompleteBackup == null
        ? 'todavía no hay un backup completo registrado.'
        : `hace ${lastBackupAge} que no hay un backup completo. Si hoy se cae el servidor, podrías perder hasta ${possibleLoss} de datos.`;
    return { tone: 'danger', lead: 'Atención:', text, needsSupport: true };
  }
  if (state === 'STALE') {
    return {
      tone: 'warning',
      lead: 'Sin noticias:',
      text: `el estado no se actualiza desde el ${formatBackupMoment(generatedAt)}. Los backups podrían estar frenados.`,
      needsSupport: true,
    };
  }
  return { tone: 'neutral', lead: 'Sin configurar:', text: 'los backups todavía no están activos en el servidor.', needsSupport: true };
}

export function diskFreePercent(disk) {
  if (!disk || !disk.totalBytes) return null;
  return Math.round((disk.freeBytes / disk.totalBytes) * 100);
}

export function diskTone(freePercent) {
  if (freePercent < 5) return { tone: 'danger', label: 'Crítico' };
  if (freePercent < 15) return { tone: 'warning', label: 'Bajo' };
  return null;
}

export const RESTORE_TEST_MAX_AGE_DAYS = 14;
const DAY_MS = 24 * 60 * 60 * 1000;

export function restoreTestReminder(restoreTest, now = new Date()) {
  if (!restoreTest) return 'Todavía no se hizo una prueba completa de restauración.';
  const ageDays = (now.getTime() - new Date(restoreTest.ranAt).getTime()) / DAY_MS;
  return ageDays > RESTORE_TEST_MAX_AGE_DAYS ? `Pasaron más de ${RESTORE_TEST_MAX_AGE_DAYS} días sin una prueba completa de restauración.` : null;
}

export function tierLabel(tier) {
  return TIER_LABELS[tier] ?? 'Otro nivel';
}

export function pillFor(pills, value) {
  return pills[value] ?? UNKNOWN_PILL;
}
