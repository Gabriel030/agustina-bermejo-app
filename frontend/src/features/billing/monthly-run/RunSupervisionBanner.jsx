import { TriangleAlert } from 'lucide-react';
import { missedRunMessage } from './monthlyRunLabels';

export function RunSupervisionBanner({ period, run, isCurrentPeriod }) {
  const message = isCurrentPeriod ? missedRunMessage(period, run) : null;
  if (!message) return null;
  return (
    <div role="alert" className="flex items-start gap-3 rounded-2xl border border-warning/40 bg-warning-bg p-4 text-sm text-ink">
      <TriangleAlert className="mt-0.5 size-5 shrink-0 text-warning" aria-hidden="true" />
      <div className="flex flex-col gap-0.5">
        <span className="font-semibold">{message}</span>
        <span className="text-ink-soft">Simulá la emisión para revisar qué se va a generar y recuperala a mano.</span>
      </div>
    </div>
  );
}
