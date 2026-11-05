import { GlassCard } from '@/components/GlassCard';
import { cn } from '@/lib/utils';
import { describeStatusChange, dueInfo, formatInstant } from './authorizationRequests';

const DOT_TONES = {
  success: 'bg-success',
  warning: 'bg-warning',
  danger: 'bg-danger',
  neutral: 'bg-neutral-ink',
};

export function RequestTimeline({ request, statusChanges }) {
  const events = statusChanges.map((change) => ({ ...describeStatusChange(change), at: change.changedAt, reason: change.reason }));
  if (request.status === 'PENDING') {
    const due = dueInfo(request.expiresOn);
    events.push({
      label: due.key === 'none' ? 'Esperando decisión · no vence' : `Esperando decisión · ${due.label.toLowerCase()}`,
      tone: 'warning',
      at: null,
      reason: null,
    });
  }

  return (
    <GlassCard className="p-5">
      <h2 className="font-heading text-base font-semibold text-ink">Historial de este pedido</h2>
      <ol className="mt-3 flex flex-col gap-3">
        {events.map((event, index) => (
          <li key={`${event.label}-${index}`} className="flex items-start gap-3">
            <span className={cn('mt-1.5 size-2.5 shrink-0 rounded-full', DOT_TONES[event.tone])} aria-hidden="true" />
            <div className="flex min-w-0 flex-col">
              <span className="text-sm font-semibold text-ink">{event.label}</span>
              {event.at && <span className="text-xs text-ink-faint">{formatInstant(event.at)}</span>}
              {event.reason && <span className="text-xs text-ink-soft">{event.reason}</span>}
            </div>
          </li>
        ))}
      </ol>
    </GlassCard>
  );
}
