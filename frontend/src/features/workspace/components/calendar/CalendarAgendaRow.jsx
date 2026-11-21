import { Button } from '@/components/ui/button';
import { StatusPill } from '@/components/StatusPill';
import { cn } from '@/lib/utils';
import {
  eventAriaLabel,
  eventMeta,
  eventStatusPill,
  isCanceled,
} from '@/features/workspace/components/calendar/calendarEventMeta';

export function CalendarAgendaRow({ event, onSelectEvent }) {
  const meta = eventMeta(event);
  const pill = eventStatusPill(event);
  const place = [event.sectionName, event.place].filter(Boolean).join(' · ');
  return (
    <Button variant="ghost" size="inline"
      aria-label={eventAriaLabel(event)}
      onClick={() => onSelectEvent(event)}
      className={cn(
        'grid min-h-11 w-full grid-cols-[4.5rem_1fr] items-center gap-x-3 gap-y-1 rounded-control border-l-[3px] bg-neutral-bg/40 px-3 py-2 text-left hover:bg-neutral-bg focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-brand sm:grid-cols-[4.5rem_1fr_auto]',
        meta.bar,
      )}
    >
      <span className="flex flex-col text-size-13 tabular-nums text-ink">
        {event.startTime ? (
          <>
            <span className="font-semibold">{event.startTime}</span>
            {event.endTime && <span className="text-size-11 text-ink-faint">{event.endTime}</span>}
          </>
        ) : (
          <span className="text-size-12 font-semibold text-ink-soft">Todo el día</span>
        )}
      </span>
      <span className="flex min-w-0 flex-col">
        <span className="flex min-w-0 items-center gap-1.5 text-size-14 font-medium text-ink">
          <span aria-hidden="true" className={cn('shrink-0', meta.tone)}>
            {meta.glyph}
          </span>
          <span className={cn('truncate', isCanceled(event) && 'line-through')}>{event.title}</span>
        </span>
        {place && <span className="truncate text-size-12 text-ink-soft">{place}</span>}
      </span>
      <StatusPill tone={pill.tone} className="col-start-2 sm:col-start-3">
        {pill.label}
      </StatusPill>
    </Button>
  );
}
