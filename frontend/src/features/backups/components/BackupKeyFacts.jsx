import { StatCard } from '@/components/StatCard';
import { ProgressBar } from '@/components/ProgressBar';
import { StatusPill } from '@/components/StatusPill';
import { formatBackupMoment, formatHours } from '@/features/backups/lib/backupFormat';

export function BackupKeyFacts({ summary }) {
  const { lastCompleteBackupAt, hoursSinceLastCompleteBackup, potentialLossHours, targetRpoHours, consecutiveFailures } = summary;
  const overTarget = potentialLossHours != null && potentialLossHours > targetRpoHours;
  const lossPercent = potentialLossHours == null || !(targetRpoHours > 0) ? 0 : (potentialLossHours / targetRpoHours) * 100;

  return (
    <div className="grid grid-cols-1 gap-3 md:grid-cols-3 md:gap-4">
      <StatCard label="Último backup completo">
        <div className="font-heading text-size-22 font-bold">{hoursSinceLastCompleteBackup == null ? '—' : `Hace ${formatHours(hoursSinceLastCompleteBackup)}`}</div>
        <div className="text-size-12-5 text-ink-soft">{formatBackupMoment(lastCompleteBackupAt)}</div>
      </StatCard>
      <StatCard label="Pérdida posible hoy">
        <div className="flex flex-wrap items-center gap-2 font-heading text-size-22 font-bold">
          {potentialLossHours == null ? '—' : `Hasta ${formatHours(potentialLossHours)}`}
          {overTarget && <StatusPill tone="danger">Supera el objetivo</StatusPill>}
        </div>
        <ProgressBar
          className="mt-1.5"
          value={lossPercent}
          tone={overTarget ? 'danger' : 'success'}
          showValue={false}
          label={`Pérdida posible contra el objetivo de ${formatHours(targetRpoHours)}`}
        />
        <div className="mt-1 flex justify-between gap-2 text-size-12 text-ink-soft">
          <span>{potentialLossHours == null ? 'Sin datos' : `${formatHours(potentialLossHours)} de datos`}</span>
          <span>Objetivo: {formatHours(targetRpoHours)}</span>
        </div>
      </StatCard>
      <StatCard label="Fallos seguidos">
        <div className="font-heading text-size-22 font-bold">{consecutiveFailures}</div>
        <div className="text-size-12-5 text-ink-soft">{consecutiveFailures === 0 ? 'La última corrida terminó bien' : 'Corridas seguidas que fallaron'}</div>
      </StatCard>
    </div>
  );
}
