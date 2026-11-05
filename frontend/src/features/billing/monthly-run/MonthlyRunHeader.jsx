import { GlassCard } from '@/components/GlassCard';
import { MonthPicker } from '@/components/MonthPicker';
import { StatusPill } from '@/components/StatusPill';
import { formatDateTime } from '@/lib/billing';
import { RUN_STATUS_LABELS, RUN_STATUS_TONES, RUN_TRIGGER_LABELS } from './monthlyRunLabels';

export function MonthlyRunHeader({ period, maxPeriod, run, onPeriodChange }) {
  return (
    <GlassCard className="flex flex-wrap items-center gap-x-4 gap-y-3 p-4">
      <MonthPicker value={period} onChange={onPeriodChange} maxMonth={maxPeriod} currentMonth={maxPeriod} />
      {run ? (
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-ink-soft">
          <StatusPill tone={RUN_STATUS_TONES[run.status] ?? 'neutral'}>{RUN_STATUS_LABELS[run.status] ?? run.status}</StatusPill>
          <span>{RUN_TRIGGER_LABELS[run.trigger] ?? run.trigger}</span>
          {run.startedAt && <span>Inició {formatDateTime(run.startedAt)}</span>}
          {run.finishedAt && <span>Terminó {formatDateTime(run.finishedAt)}</span>}
        </div>
      ) : (
        <StatusPill tone="neutral">Sin corrida</StatusPill>
      )}
    </GlassCard>
  );
}
