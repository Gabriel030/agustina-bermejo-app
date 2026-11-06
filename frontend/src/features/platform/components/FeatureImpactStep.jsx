import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/Skeleton';
import { IMPACT_CARDS, formatCountdown } from '@/features/platform/lib/impactCards';

export function FeatureImpactStep({ query, secondsLeft, onRecalculate }) {
  if (query.isLoading) {
    return (
      <div role="status" className="grid grid-cols-2 gap-2" aria-busy="true" aria-label="Calculando el impacto">
        {IMPACT_CARDS.map((card) => <Skeleton key={card.field} className="h-16 w-full" />)}
      </div>
    );
  }
  if (query.isError) {
    return (
      <div role="alert" className="flex flex-col items-start gap-2 text-sm text-danger">
        <span className="font-semibold">No pudimos calcular el impacto del cambio.</span>
        <Button variant="outline" onClick={() => query.refetch()}>Reintentar</Button>
      </div>
    );
  }
  const { impact } = query.data;
  const expired = secondsLeft === 0;
  return (
    <div className="flex flex-col gap-3">
      <ul className="grid grid-cols-2 gap-2">
        {IMPACT_CARDS.map((card) => (
          <li key={card.field} className="rounded-xl border border-line p-3">
            <span className="block text-xl font-semibold text-ink">{impact[card.field]}</span>
            <span className="text-xs text-ink-soft">{card.label}</span>
          </li>
        ))}
      </ul>
      {impact.scheduledJobs.length > 0 && (
        <div className="rounded-xl border border-line p-3 text-sm">
          <span className="font-semibold text-ink">Tareas programadas que se detienen</span>
          <ul className="mt-1 list-disc pl-5 text-ink-soft">
            {impact.scheduledJobs.map((job) => <li key={job}>{job}</li>)}
          </ul>
        </div>
      )}
      {expired ? (
        <div role="alert" className="flex items-center justify-between gap-2 rounded-xl bg-warning-bg p-3 text-sm text-warning">
          <b>La verificación venció.</b>
          <Button variant="outline" onClick={onRecalculate} loading={query.isFetching}>Recalcular</Button>
        </div>
      ) : (
        <p className="text-xs text-ink-faint">
          Esta verificación vale <b>{formatCountdown(secondsLeft ?? 0)}</b>. Si vence, hay que recalcularla.
        </p>
      )}
    </div>
  );
}
