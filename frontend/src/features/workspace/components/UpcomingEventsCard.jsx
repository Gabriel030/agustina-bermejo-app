import { Link } from 'react-router-dom';
import { ChevronRight, RefreshCw, TriangleAlert } from 'lucide-react';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { parsearFecha } from '@/lib/comisiones';
import { cn } from '@/lib/utils';
import { CardFrame, InfoTip } from '@/features/workspace/components/HomeTaskCard';

const UPCOMING_TIP = 'Lo que viene en el calendario: mesas de examen, feriados, cierres de período e inscripciones.';

const EVENT_TYPE_PILL = {
  exam: { tone: 'accent', label: 'Mesa de examen' },
  holiday: { tone: 'neutral', label: 'Feriado' },
  close: { tone: 'warning', label: 'Cierre de período' },
  enrollment: { tone: 'success', label: 'Inscripción' },
  start: { tone: 'neutral', label: 'Inicio de período' },
  class: { tone: 'accent', label: 'Clase' },
  due: { tone: 'warning', label: 'Vencimiento' },
  assignment: { tone: 'neutral', label: 'Entrega' },
};

const EVENT_ROW_CLASS = 'grid grid-cols-[3.5rem_minmax(0,1fr)_auto] min-[521px]:grid-cols-[3.5rem_minmax(0,1fr)_auto_auto] items-center gap-3 border-t border-border px-1 py-2';

function eventDateParts(isoDate) {
  const date = parsearFecha(isoDate);
  const day = String(date.getDate()).padStart(2, '0');
  const weekday = date.toLocaleDateString('es-AR', { weekday: 'short' }).replace('.', '');
  const month = date.toLocaleDateString('es-AR', { month: 'short' }).replace('.', '');
  return { day, weekdayMonth: `${weekday} ${month}` };
}

function EventDateBlock({ isoDate }) {
  const { day, weekdayMonth } = eventDateParts(isoDate);
  return (
    <time dateTime={isoDate} className="grid h-11 w-14 place-items-center rounded-control bg-neutral-bg text-center leading-tight">
      <span className="font-heading text-size-15 font-bold tabular-nums text-ink">{day}</span>
      <span className="text-size-10 font-extrabold uppercase text-ink-faint">{weekdayMonth}</span>
    </time>
  );
}

function EventRowContent({ event }) {
  const pill = EVENT_TYPE_PILL[event.type];
  return (
    <>
      <EventDateBlock isoDate={event.date} />
      <span className="min-w-0">
        <span className="block truncate text-size-13-5 font-semibold text-ink">{event.title}</span>
        {event.subtitle && <span className="block truncate text-size-12 text-ink-faint tabular-nums">{event.subtitle}</span>}
      </span>
      <StatusPill tone={pill.tone} className="max-[520px]:sr-only min-[521px]:not-sr-only">
        {pill.label}
      </StatusPill>
      {event.to ? <ChevronRight className="h-4 w-4 text-ink-faint" aria-hidden="true" /> : <span className="w-4" aria-hidden="true" />}
    </>
  );
}

function UpcomingEventRow({ event }) {
  if (!event.to) {
    return (
      <li className={EVENT_ROW_CLASS}>
        <EventRowContent event={event} />
      </li>
    );
  }
  return (
    <li>
      <Link to={event.to} className={cn(EVENT_ROW_CLASS, 'rounded-control transition-colors hover:bg-neutral-bg/60 focus-visible:outline-2 focus-visible:outline-primary')}>
        <EventRowContent event={event} />
      </Link>
    </li>
  );
}

function UpcomingEventsLoading({ card }) {
  return (
    <CardFrame card={card} tone="neutral">
      <div className="col-span-2 flex flex-col" role="status" aria-busy="true">
        <span className="sr-only">Cargando próximos eventos</span>
        <h3 className="flex min-h-9 items-center font-heading text-size-15 font-semibold text-ink">{card.title}</h3>
        {[0, 1, 2].map((row) => (
          <div key={row} className={EVENT_ROW_CLASS}>
            <Skeleton className="h-11 w-14 rounded-control" />
            <Skeleton className="h-3.5 w-3/5" />
          </div>
        ))}
      </div>
      <div className="col-start-3 flex h-9 items-center">
        <InfoTip text={UPCOMING_TIP} />
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
    <CardFrame card={card} tone="neutral">
      <div className="col-span-2 flex min-w-0 flex-col">
        <h3 className="flex min-h-9 items-center font-heading text-size-15 font-semibold leading-tight text-ink">{card.title}</h3>
        {card.events.length === 0 ? (
          <div className="flex flex-col items-center gap-1 py-6 text-center">
            <p className="font-semibold text-ink">No hay eventos próximos</p>
            <p className="text-size-13 text-ink-faint">Cuando se programe algo, lo vas a ver acá.</p>
          </div>
        ) : (
          <ol className="flex flex-col">
            {card.events.map((event) => (
              <UpcomingEventRow key={event.id} event={event} />
            ))}
          </ol>
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
