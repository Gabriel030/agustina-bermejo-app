import { StatCard } from '@/components/StatCard';
import { ProgressBar } from '@/components/ProgressBar';
import { StatusPill } from '@/components/StatusPill';
import { restoreTestFailureLabel } from '@/features/backups/lib/backupLabels';
import { RESTORE_RESULT_PILLS, diskFreePercent, diskTone, formatBackupMoment, formatBytes, pillFor } from '@/features/backups/lib/backupFormat';
import { formatPercent } from '@/lib/formatNumber';

function RestoreTestCard({ restoreTest }) {
  if (!restoreTest) {
    return (
      <StatCard label="Prueba de restauración">
        <div className="font-heading text-size-17 font-bold">Sin pruebas todavía</div>
        <div className="text-size-12-5 text-ink-soft">Se prueba una vez por semana.</div>
      </StatCard>
    );
  }
  const pill = pillFor(RESTORE_RESULT_PILLS, restoreTest.result);
  return (
    <StatCard label="Prueba de restauración">
      <div className="flex flex-wrap items-center gap-2 font-heading text-size-17 font-bold">
        <StatusPill tone={pill.tone}>{pill.label}</StatusPill>
        <span>{formatBackupMoment(restoreTest.ranAt)}</span>
      </div>
      <div className="text-size-12-5 text-ink-soft">
        {restoreTest.result === 'FAILED' ? `${restoreTestFailureLabel(restoreTest.failureCode)}. ` : ''}Se prueba una vez por semana.
      </div>
    </StatCard>
  );
}

function DiskCard({ disk, isPlatformAdmin }) {
  const freePercent = diskFreePercent(disk);
  if (freePercent == null) {
    return (
      <StatCard label="Disco de backups">
        <div className="font-heading text-size-17 font-bold">Sin datos</div>
      </StatCard>
    );
  }
  const warning = diskTone(freePercent);
  const usedPercent = 100 - freePercent;
  return (
    <StatCard label="Disco de backups">
      <div className="flex flex-wrap items-center gap-2 font-heading text-size-17 font-bold">
        {formatPercent(freePercent, { maximumFractionDigits: 0 })} libre
        {warning && <StatusPill tone={warning.tone}>{warning.label}</StatusPill>}
      </div>
      <ProgressBar
        className="mt-1.5"
        value={usedPercent}
        tone={warning?.tone ?? 'accent'}
        showValue={false}
        label="Espacio usado del disco de backups"
      />
      <div className="mt-1 flex flex-wrap justify-between gap-x-2 text-size-12 text-ink-soft">
        <span>
          Usado {formatBytes(disk.totalBytes - disk.freeBytes)} de {formatBytes(disk.totalBytes)}
        </span>
        {isPlatformAdmin && disk.path && <span className="font-mono text-size-11-5 break-all">{disk.path}</span>}
      </div>
    </StatCard>
  );
}

export function BackupRestoreAndDisk({ restoreTest, disk, isPlatformAdmin }) {
  return (
    <div className="grid grid-cols-1 gap-3 md:grid-cols-2 md:gap-4">
      <RestoreTestCard restoreTest={restoreTest} />
      <DiskCard disk={disk} isPlatformAdmin={isPlatformAdmin} />
    </div>
  );
}
