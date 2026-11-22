import { useRef } from 'react';
import { Link } from 'react-router-dom';
import { X } from 'lucide-react';
import { Notice } from '@/components/Notice';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { cn } from '@/lib/utils';
import { eventMeta, eventStatusPill, isCanceled } from '@/features/workspace/components/calendar/calendarEventMeta';
import { formatEventHours } from '@/features/workspace/lib/calendarFeed';
import { formatDayLong } from '@/features/workspace/lib/studentCalendar';

function eventAction(event) {
  if (!event.link) return null;
  if (event.type === 'CLASS') return { label: 'Ver la materia', variant: 'outline' };
  if (event.type === 'EXAM_BOARD' && event.status === 'OPEN_FOR_REGISTRATION') {
    return { label: 'Ir a Mesas de examen para anotarme', variant: 'primary' };
  }
  if (event.type === 'EXAM_BOARD' && event.status === 'REGISTERED') {
    return { label: 'Ver mi inscripción en Mesas de examen', variant: 'outline' };
  }
  return null;
}

function DetailRow({ label, children }) {
  return (
    <div className="grid grid-cols-[5rem_minmax(0,1fr)] items-center gap-3 border-t border-border py-2.5 first:border-t-0">
      <dt className="text-size-12 font-semibold text-ink-faint">{label}</dt>
      <dd className="min-w-0 text-size-14 text-ink">{children}</dd>
    </div>
  );
}

function EventDetailBody({ event }) {
  const pill = eventStatusPill(event);
  const hours = formatEventHours(event);
  const canceled = isCanceled(event);
  const action = eventAction(event);
  return (
    <div className="flex flex-col gap-4 p-5 pt-2">
      <dl className="flex flex-col">
        <DetailRow label="Fecha">{formatDayLong(event.date, null)}</DetailRow>
        {hours && <DetailRow label="Horario">{hours}</DetailRow>}
        {event.place && <DetailRow label="Aula">{event.place}</DetailRow>}
        <DetailRow label="Estado">
          <StatusPill tone={pill.tone}>{pill.label}</StatusPill>
        </DetailRow>
      </dl>
      {event.reason && (
        <Notice tone={canceled ? 'danger' : 'neutral'}>
          {canceled ? 'Motivo' : 'Aviso'}: {event.reason}
        </Notice>
      )}
      {event.type === 'HOLIDAY' && <Notice>No hay clases este día. Las clases habituales no se dictan.</Notice>}
      {action && (
        <Button asChild variant={action.variant} className="min-h-11 h-auto whitespace-normal text-center">
          <Link to={event.link}>{action.label}</Link>
        </Button>
      )}
    </div>
  );
}

export function CalendarEventDetail({ event, onClose, onCloseAutoFocus, isDesktop }) {
  const closeRef = useRef(null);
  const meta = event ? eventMeta(event) : null;
  return (
    <Sheet open={event !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent
        side={isDesktop ? 'right' : 'bottom'}
        showCloseButton={false}
        aria-describedby="calendar-event-description"
        onCloseAutoFocus={onCloseAutoFocus}
        onOpenAutoFocus={(focusEvent) => {
          focusEvent.preventDefault();
          closeRef.current?.focus();
        }}
        className="max-h-[90dvh] gap-0 overflow-y-auto data-[side=right]:max-h-none data-[side=right]:w-full data-[side=right]:sm:max-w-[400px] max-md:rounded-t-3xl"
      >
        {event && (
          <>
            <SheetHeader className="flex-row items-start justify-between gap-3 p-5 pb-3">
              <div className="flex min-w-0 flex-col gap-1">
                <span className={cn('flex items-center gap-1.5 text-size-12 font-bold', meta.tone)}>
                  <span aria-hidden="true">{meta.glyph}</span>
                  {meta.label}
                </span>
                <SheetTitle asChild>
                  <h3 className="font-heading text-size-17 font-bold text-ink">
                    {isCanceled(event) && event.type === 'CLASS' ? `Clase cancelada: ${event.title}` : event.title}
                  </h3>
                </SheetTitle>
                <SheetDescription id="calendar-event-description" className="sr-only">
                  Detalle del evento del calendario
                </SheetDescription>
              </div>
              <SheetClose asChild>
                <Button ref={closeRef} variant="ghost" size="icon" aria-label="Cerrar detalle" className="-mr-2 size-11 shrink-0">
                  <X aria-hidden="true" />
                </Button>
              </SheetClose>
            </SheetHeader>
            <EventDetailBody event={event} />
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
