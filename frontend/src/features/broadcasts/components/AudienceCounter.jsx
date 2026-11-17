import { GlassCard } from '@/components/GlassCard';
import { StatCard } from '@/components/StatCard';
import { KpiFilterChip } from '@/components/KpiFilterChip';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Skeleton } from '@/components/Skeleton';
import { EXCLUSION_REASON_LABELS } from '../lib/broadcastWizard';

export function AudienceCounter({ preview, isError, error, onRetry, recalculating, reasonFilter, onReasonFilter }) {
  if (isError && !preview) {
    return <LoadErrorState error={error} onRetry={onRetry} resource="los destinatarios" />;
  }
  if (!preview) {
    return (
      <GlassCard className="flex flex-col gap-3 p-5">
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-16 w-full" />
      </GlassCard>
    );
  }
  if (preview.total === 0) {
    return (
      <GlassCard className="p-5">
        <EmptyState icon="👥" title="Sin destinatarios" description="Ningún alumno coincide con estos criterios." />
      </GlassCard>
    );
  }
  const reasons = Object.entries(preview.excludedByReason ?? {}).filter(([, count]) => count > 0);
  return (
    <div className="flex flex-col gap-3" aria-busy={recalculating}>
      <div className="grid grid-cols-2 gap-3">
        <StatCard label="Reciben">
          <span className="font-heading text-3xl font-bold text-ink">{preview.included}</span>
        </StatCard>
        <StatCard label="Excluidos">
          <span className="font-heading text-3xl font-bold text-warning">{preview.total - preview.included}</span>
        </StatCard>
      </div>
      {reasons.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {reasons.map(([reason, count]) => (
            <KpiFilterChip
              key={reason}
              label={EXCLUSION_REASON_LABELS[reason] ?? reason}
              count={count}
              pressed={reasonFilter === reason}
              onClick={() => onReasonFilter(reasonFilter === reason ? null : reason)}
            />
          ))}
        </div>
      )}
    </div>
  );
}
