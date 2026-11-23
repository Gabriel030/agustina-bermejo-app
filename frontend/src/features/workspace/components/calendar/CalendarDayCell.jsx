import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { CalendarEventButton } from '@/features/workspace/components/calendar/CalendarEventButton';
import { dayAriaLabel, dayMarks } from '@/features/workspace/components/calendar/calendarEventMeta';
import { parseDateKey } from '@/features/workspace/lib/studentCalendar';

const MAX_VISIBLE_EVENTS = 3;

function DayNumber({ dateKey, todayKey, selected, events, onSelectDay, className }) {
  const isToday = dateKey === todayKey;
  return (
    <Button variant="ghost" size="inline"
      aria-label={dayAriaLabel(dateKey, todayKey, events)}
      aria-current={isToday ? 'date' : undefined}
      aria-pressed={selected}
      onClick={() => onSelectDay(dateKey)}
      className={cn(
        'flex size-7 items-center justify-center rounded-full text-size-13 font-semibold tabular-nums focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-brand',
        isToday ? 'bg-brand text-on-accent' : 'text-ink hover:bg-neutral-bg',
        className,
      )}
    >
      {parseDateKey(dateKey).getDate()}
    </Button>
  );
}

export function CalendarDayCell({ dateKey, todayKey, inMonth, selected, events, onSelectDay, onSelectEvent }) {
  const hasHoliday = events.some((event) => event.type === 'HOLIDAY');
  const visible = events.slice(0, MAX_VISIBLE_EVENTS);
  const hidden = events.length - visible.length;
  return (
    <div
      className={cn(
        'flex min-h-28 min-w-0 flex-col gap-1 border p-1.5',
        inMonth ? 'border-border' : 'border-dashed border-border opacity-60',
        hasHoliday && 'bg-warning-bg',
        selected && 'outline-2 -outline-offset-2 outline-brand',
      )}
    >
      <DayNumber dateKey={dateKey} todayKey={todayKey} selected={selected} events={events} onSelectDay={onSelectDay} />
      {visible.map((event) => (
        <CalendarEventButton key={event.id} event={event} onSelectEvent={onSelectEvent} />
      ))}
      {hidden > 0 && (
        <Button variant="ghost" size="inline"
          onClick={() => onSelectDay(dateKey)}
          className="justify-start rounded-control px-1.5 text-left text-size-12 font-medium text-brand hover:underline focus-visible:outline-2 focus-visible:outline-brand"
        >
          +{hidden} más
        </Button>
      )}
    </div>
  );
}

export function CalendarCompactDayCell({ dateKey, todayKey, inMonth, selected, events, onSelectDay }) {
  const isToday = dateKey === todayKey;
  const hasHoliday = events.some((event) => event.type === 'HOLIDAY');
  const marks = dayMarks(events);
  return (
    <Button variant="ghost" size="inline"
      aria-label={dayAriaLabel(dateKey, todayKey, events)}
      aria-current={isToday ? 'date' : undefined}
      aria-pressed={selected}
      onClick={() => onSelectDay(dateKey)}
      className={cn(
        'flex min-h-11 min-w-0 flex-col items-center justify-center gap-1 border py-1 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brand',
        inMonth ? 'border-border' : 'border-dashed border-border opacity-60',
        hasHoliday && 'bg-warning-bg',
        selected && 'outline-2 -outline-offset-2 outline-brand',
      )}
    >
      <span
        className={cn(
          'flex size-7 items-center justify-center rounded-full text-size-13 font-semibold tabular-nums',
          isToday ? 'bg-brand text-on-accent' : 'text-ink',
        )}
      >
        {parseDateKey(dateKey).getDate()}
      </span>
      <span aria-hidden="true" className="flex h-3 items-center gap-0.5">
        {marks.map((mark) => (
          <span key={mark.key} className={cn('text-size-11 leading-none', mark.tone)}>
            {mark.glyph}
          </span>
        ))}
      </span>
    </Button>
  );
}
