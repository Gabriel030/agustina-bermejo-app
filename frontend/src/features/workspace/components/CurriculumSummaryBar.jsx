import { ArrowLeftRight } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { ProgressBar } from '@/components/ProgressBar';
import { Button } from '@/components/ui/button';
import { PLAN_STATUS_PILL } from '@/features/workspace/components/planStatusPills';
import { cn } from '@/lib/utils';

const SUMMARY_COUNTS = [
  { key: 'approved', filterId: 'aprobadas', status: 'aprobada', label: 'Aprobadas' },
  { key: 'inProgress', filterId: 'en-curso', status: 'en-curso', label: 'En curso' },
  { key: 'regularPendingFinal', filterId: 'regulares', status: 'regular', label: 'Cursada regular' },
  { key: 'available', filterId: 'disponibles', status: 'disponible', label: 'Disponibles' },
  { key: 'blocked', filterId: 'bloqueadas', status: 'bloqueada', label: 'Bloqueadas' },
  { key: 'failed', filterId: 'desaprobadas', status: 'desaprobada', label: 'Desaprobadas' },
];

function computeProgress(summary) {
  const creditsDefined = summary.requiredCredits > 0;
  const mandatoryPercent = summary.mandatoryTotal > 0 ? (summary.mandatoryApproved / summary.mandatoryTotal) * 100 : 0;
  return {
    creditsDefined,
    mandatoryPercent,
    headlinePercent: creditsDefined ? summary.completionPercent : mandatoryPercent,
  };
}

function formatPercent(value) {
  return `${Math.round(Number(value) || 0)} %`;
}

export function CurriculumSummaryBar({ summary, statusFilter, onStatusFilterChange }) {
  const { creditsDefined, mandatoryPercent, headlinePercent } = computeProgress(summary);

  function toggleFilter(filterId) {
    onStatusFilterChange(statusFilter === filterId ? 'todas' : filterId);
  }

  return (
    <GlassCard role="region" aria-labelledby="curriculum-summary-title" className="flex flex-col gap-3 p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <h2 id="curriculum-summary-title" className="font-heading text-base font-bold text-ink">
          Tu avance en el plan
        </h2>
        <span className="font-heading text-2xl font-bold tabular-nums text-ink">{formatPercent(headlinePercent)}</span>
      </div>
      <div className="flex flex-col gap-2">
        <ProgressBar
          value={mandatoryPercent}
          label={`Obligatorias aprobadas · ${summary.mandatoryApproved} de ${summary.mandatoryTotal}`}
          showValue={false}
        />
        {creditsDefined && (
          <ProgressBar
            value={summary.completionPercent}
            label={`Créditos · ${summary.accumulatedCredits} de ${summary.requiredCredits}`}
            showValue={false}
          />
        )}
      </div>
      {summary.approvedByEquivalence > 0 && (
        <div className="flex items-center gap-1.5 text-size-13 text-ink-soft">
          <ArrowLeftRight className="h-3.5 w-3.5 shrink-0 text-brand" aria-hidden="true" />
          <span>
            <span className="tabular-nums">{summary.approvedByEquivalence}</span> por equivalencia
          </span>
        </div>
      )}
      <ul aria-label="Materias por estado" className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        {SUMMARY_COUNTS.map(({ key, filterId, status, label }) => {
          const { Icon } = PLAN_STATUS_PILL[status];
          const selected = statusFilter === filterId;
          return (
            <li key={key}>
              <Button
                type="button"
                variant="ghost"
                aria-pressed={selected}
                aria-label={`Ver ${label.toLowerCase()}: ${summary.counts[key]}`}
                onClick={() => toggleFilter(filterId)}
                className={cn(
                  'h-auto min-h-11 w-full justify-start gap-2 rounded-lg px-3 text-left text-size-13 font-normal transition-colors motion-reduce:transition-none',
                  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                  selected ? 'bg-accent-soft text-brand' : 'bg-neutral-bg/60 text-ink-soft hover:bg-neutral-bg',
                )}
              >
                <Icon className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                <span className="font-heading text-base font-bold tabular-nums text-ink">{summary.counts[key]}</span>
                <span className="min-w-0 truncate">{label}</span>
              </Button>
            </li>
          );
        })}
      </ul>
    </GlassCard>
  );
}
