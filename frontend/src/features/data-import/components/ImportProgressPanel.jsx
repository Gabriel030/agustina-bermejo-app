import { Hourglass } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { ProgressBar } from '@/components/ProgressBar';
import { blockLabel } from '../importBlocks';
import { blockProgress, countOf } from '../importLabels';

export function ImportProgressPanel({ batch }) {
  const done = countOf(batch.counters, 'COMMITTED');
  return (
    <GlassCard className="flex flex-col gap-4 p-5" aria-live="polite">
      <div className="flex items-center gap-2">
        <Hourglass className="size-5 shrink-0 text-brand motion-safe:animate-pulse" aria-hidden="true" />
        <span className="font-heading text-3xl font-bold text-ink tabular-nums" data-testid="import-progress-done">
          {done.toLocaleString('es-AR')}
        </span>
        <span className="text-sm text-ink-soft">filas importadas</span>
      </div>
      <p className="text-sm text-ink-faint">Se actualiza sola. Podés salir de esta pantalla: la importación sigue.</p>
      <div className="flex flex-col gap-3">
        {batch.counters.map((block) => (
          <ProgressBar key={block.entityType} label={blockLabel(block.entityType)} value={blockProgress(block)} />
        ))}
      </div>
    </GlassCard>
  );
}
