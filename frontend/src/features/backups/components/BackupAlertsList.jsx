import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { StatusPill } from '@/components/StatusPill';
import { backupAlertLabel } from '@/features/backups/lib/backupLabels';
import { ALERT_LEVEL_PILLS, formatBackupMoment, pillFor, restoreTestReminder } from '@/features/backups/lib/backupFormat';

export function BackupAlertsList({ alerts, restoreTest }) {
  const reminder = restoreTestReminder(restoreTest);
  const warning = ALERT_LEVEL_PILLS.WARNING;
  return (
    <section aria-labelledby="backup-alerts-heading">
      <GlassCard className="p-0">
        <div className="flex flex-wrap items-baseline justify-between gap-2 px-5 py-4">
          <h2 id="backup-alerts-heading" className="font-heading text-size-16 font-bold text-ink">
            Alertas recientes
          </h2>
          <span className="text-size-12 text-ink-soft">Últimos 30 días</span>
        </div>
        {alerts.length === 0 && !reminder ? (
          <div className="border-t border-border">
            <EmptyState icon="✅" title="Sin alertas" description="No hubo alertas en los últimos 30 días." />
          </div>
        ) : (
          <ul className="border-t border-border">
            {reminder && (
              <li className="grid grid-cols-[auto_1fr] items-center gap-x-3 gap-y-1 border-b border-border px-5 py-3 text-size-14 md:grid-cols-[6rem_8rem_minmax(0,1fr)]">
                <StatusPill tone={warning.tone}>{warning.label}</StatusPill>
                <span className="text-size-12-5 text-ink-soft">Hoy</span>
                <span className="col-span-2 md:col-span-1">{reminder}</span>
              </li>
            )}
            {alerts.map((alert) => {
              const pill = pillFor(ALERT_LEVEL_PILLS, alert.level);
              return (
                <li
                  key={`${alert.at}-${alert.code}`}
                  className="grid grid-cols-[auto_1fr] items-center gap-x-3 gap-y-1 border-b border-border px-5 py-3 text-size-14 last:border-b-0 md:grid-cols-[6rem_8rem_minmax(0,1fr)]"
                >
                  <StatusPill tone={pill.tone}>{pill.label}</StatusPill>
                  <time dateTime={alert.at} className="text-size-12-5 text-ink-soft tabular-nums">
                    {formatBackupMoment(alert.at)}
                  </time>
                  <span className="col-span-2 md:col-span-1">{backupAlertLabel(alert.code)}</span>
                </li>
              );
            })}
          </ul>
        )}
      </GlassCard>
    </section>
  );
}
