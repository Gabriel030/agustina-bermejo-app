import { useState } from 'react';
import { Clock, HelpCircle } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { useOwnWaitlistPositions } from '@/features/enrollment/hooks/useOwnWaitlistPositions';
import { describeWaitlistExpiry, findOwnWaitlistPosition, QUEUE_BAR_MAX_SEGMENTS } from '@/features/enrollment/lib/waitlistPosition';

const EXPIRY_HELP_TEXT = 'Si no hay lugar al cierre, la espera termina y te avisamos.';

function QueueBar({ position, totalWaiting }) {
  return (
    <div role="img" aria-label={`Lugar ${position} de ${totalWaiting}`} className="flex gap-1">
      {Array.from({ length: totalWaiting }, (_, index) => (
        <span key={index} className={`h-2 flex-1 rounded-full ${index + 1 === position ? 'bg-brand' : 'bg-neutral-bg'}`} />
      ))}
    </div>
  );
}

function ExpiryHelp() {
  const [open, setOpen] = useState(false);
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button type="button" variant="text" size="inline" aria-expanded={open} className="min-h-11 gap-1.5 px-2 text-size-13 font-semibold">
          <HelpCircle className="size-4" aria-hidden="true" />
          ¿Qué pasa si vence?
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start">{EXPIRY_HELP_TEXT}</PopoverContent>
    </Popover>
  );
}

export function WaitlistPositionCard({ entry, showName = true }) {
  const { queueName, position, totalWaiting, expiresOn } = entry;
  const expiry = expiresOn ? describeWaitlistExpiry(expiresOn) : null;
  const isNext = position === 1;
  return (
    <GlassCard className="flex flex-col gap-3 p-4 sm:p-5" role="group" aria-label={`Lista de espera: ${queueName}`}>
      {(showName || isNext) && (
        <div className="flex flex-wrap items-center justify-between gap-2">
          {showName && <span className="font-heading text-size-15 font-semibold text-ink">{queueName}</span>}
          {isNext && <StatusPill tone="success">Sos el próximo</StatusPill>}
        </div>
      )}
      <div className="flex flex-col gap-0.5">
        <span className="text-size-11 font-semibold uppercase tracking-wide text-ink-faint">Tu lugar</span>
        <span className="flex items-baseline gap-2">
          <span className="font-heading text-4xl font-bold tabular-nums text-ink">{position}</span>
          <span className="text-size-13 tabular-nums text-ink-soft">de {totalWaiting}</span>
        </span>
      </div>
      {totalWaiting <= QUEUE_BAR_MAX_SEGMENTS && <QueueBar position={position} totalWaiting={totalWaiting} />}
      {expiry && (
        <div className="flex flex-wrap items-center gap-2">
          <StatusPill tone={expiry.urgent ? 'danger' : 'neutral'} icon={Clock}>
            {expiry.label}
          </StatusPill>
          <ExpiryHelp />
        </div>
      )}
    </GlassCard>
  );
}

export function OwnWaitlistPosition({ kind, enrollmentId, showName = true }) {
  const { data, isLoading, isError, error, refetch } = useOwnWaitlistPositions();
  if (isLoading) {
    return (
      <div role="status" aria-label="Cargando tu lugar">
        <Skeleton className="h-28" />
      </div>
    );
  }
  if (isError) return <LoadErrorState bare error={error} onRetry={refetch} resource="tu lugar" />;
  const entry = findOwnWaitlistPosition(data, kind, enrollmentId);
  if (!entry) return null;
  return <WaitlistPositionCard entry={entry} showName={showName} />;
}
