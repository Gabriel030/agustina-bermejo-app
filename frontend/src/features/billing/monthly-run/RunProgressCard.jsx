import { Hourglass } from 'lucide-react';
import { StatCard } from '@/components/StatCard';

export function RunProgressCard({ counters }) {
  const processed = (counters?.issued ?? 0) + (counters?.alreadyIssued ?? 0) + (counters?.notApplicable ?? 0) + (counters?.failed ?? 0) + (counters?.retriesExhausted ?? 0);
  return (
    <StatCard label="Corrida en curso">
      <div className="flex items-center gap-2">
        <Hourglass className="size-5 shrink-0 text-brand motion-safe:animate-pulse" aria-hidden="true" />
        <span className="font-heading text-3xl font-bold text-ink" data-testid="run-progress-processed">
          {processed}
        </span>
        <span className="text-sm text-ink-soft">inscripciones procesadas</span>
      </div>
      <div role="progressbar" aria-label="Avance de la corrida" className="mt-3 h-2 w-full overflow-hidden rounded-full bg-neutral-bg">
        <div className="h-full w-1/3 rounded-full bg-gradient-to-r from-brand to-accent-2 motion-safe:animate-pulse" />
      </div>
      <div className="mt-1 text-xs text-ink-faint">Se actualiza sola. Hasta que termine no se puede emitir ni reintentar.</div>
    </StatCard>
  );
}
