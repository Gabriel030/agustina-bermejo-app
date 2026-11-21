import { RefreshCw, TriangleAlert } from 'lucide-react';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { parsearFecha } from '@/lib/comisiones';
import { CardFrame, InfoTip } from '@/features/workspace/components/HomeTaskCard';

const UPCOMING_TIP = 'Mesas de examen programadas, feriados, inicio y fin de período, y apertura y cierre de inscripciones en los próximos 30 días.';
const EVENT_DATE_FORMAT = { day: 'numeric', month: 'short' };

function shortDate(isoDate) {
  return parsearFecha(isoDate).toLocaleDateString('es-AR', EVENT_DATE_FORMAT);
}

function UpcomingEventsLoading({ card }) {
  return (
    <CardFrame card={card} tone="neutral">
      <div className="col-span-2 flex flex-col gap-2" aria-busy="true">
        <Skeleton className="mt-2 h-4 w-1/3" />
        <Skeleton className="h-8 w-1/5" />
        <Skeleton className="h-3.5 w-full" />
      </div>
    </CardFrame>
  );
}

function UpcomingEventsError({ card }) {
  return (
    <CardFrame card={card} tone="neutral" className="border-danger">
      <div className="col-span-2 flex flex-col gap-2">
        <h3 className="flex min-h-9 items-center font-heading text-size-15 font-semibold text-ink">{card.title}</h3>
        <div role="alert" className="flex items-center gap-2 text-size-13-5 font-semibold text-danger-ink">
          <TriangleAlert className="h-[18px] w-[18px]" />
          <span>No pudimos cargar los próximos eventos</span>
        </div>
        <div>
          <Button type="button" variant="text" onClick={card.retry}>
            <RefreshCw className="h-4 w-4" />
            Reintentar
          </Button>
        </div>
      </div>
    </CardFrame>
  );
}

function UpcomingEventsReady({ card }) {
  return (
    <CardFrame card={card} tone="neutral" className="sm:col-span-2">
      <div className="flex min-w-0 flex-col gap-2">
        <h3 className="flex min-h-9 items-center font-heading text-size-15 font-semibold leading-tight text-ink">{card.title}</h3>
        {card.events.length === 0 ? (
          <p className="text-size-13-5 font-medium text-ink-soft">Sin eventos en los próximos 30 días.</p>
        ) : (
          <>
            <div className="flex min-h-8 items-baseline gap-1.5">
              <span className="font-heading text-3xl font-semibold leading-none tabular-nums text-ink">{card.count}</span>
              <span className="text-size-13 text-ink-faint">{card.count === 1 ? 'evento' : 'eventos'}</span>
            </div>
            <ol className="flex flex-col">
              {card.events.map((event) => (
                <li key={event.id} className="flex min-h-10 items-center gap-3 border-t border-border text-size-13-5 font-medium text-ink">
                  <time dateTime={event.date} className="w-14 shrink-0 font-semibold tabular-nums text-ink-soft">
                    {shortDate(event.date)}
                  </time>
                  <span className="min-w-0">{event.title}</span>
                </li>
              ))}
            </ol>
          </>
        )}
      </div>
      <div className="col-start-3 flex h-9 items-center">
        <InfoTip text={UPCOMING_TIP} />
      </div>
    </CardFrame>
  );
}

export function UpcomingEventsCard({ card }) {
  if (card.state === 'loading') return <UpcomingEventsLoading card={card} />;
  if (card.state === 'error') return <UpcomingEventsError card={card} />;
  return <UpcomingEventsReady card={card} />;
}
