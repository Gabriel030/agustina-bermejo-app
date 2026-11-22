import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { CalendarCompactDayCell, CalendarDayCell } from '@/features/workspace/components/calendar/CalendarDayCell';
import { CalendarEventButton } from '@/features/workspace/components/calendar/CalendarEventButton';
import { dayAriaLabel } from '@/features/workspace/components/calendar/calendarEventMeta';
import { WEEKDAY_INITIALS, parseDateKey } from '@/features/workspace/lib/studentCalendar';

function WeekdayHeader() {
  return (
    <div aria-hidden="true" className="grid grid-cols-7 text-center text-size-12 font-semibold text-ink-soft">
      {WEEKDAY_INITIALS.map((initial, index) => (
        <span key={index} className="py-1">
          {initial}
        </span>
      ))}
    </div>
  );
}

export function CalendarMonthGrid({ days, monthKey, eventsByDay, todayKey, selectedKey, compact, onSelectDay, onSelectEvent }) {
  const Cell = compact ? CalendarCompactDayCell : CalendarDayCell;
  return (
    <div className="flex flex-col">
      <WeekdayHeader />
      <div className="grid grid-cols-7">
        {days.map((dateKey) => (
          <Cell
            key={dateKey}
            dateKey={dateKey}
            todayKey={todayKey}
            inMonth={dateKey.startsWith(monthKey)}
            selected={dateKey === selectedKey}
            events={eventsByDay.get(dateKey) ?? []}
            onSelectDay={onSelectDay}
            onSelectEvent={onSelectEvent}
          />
        ))}
      </div>
    </div>
  );
}

export function CalendarWeekColumns({ days, eventsByDay, todayKey, selectedKey, onSelectDay, onSelectEvent }) {
  return (
    <div className="grid grid-cols-7">
      {days.map((dateKey) => {
        const events = eventsByDay.get(dateKey) ?? [];
        const isToday = dateKey === todayKey;
        return (
          <div
            key={dateKey}
            className={cn(
              'flex min-h-64 min-w-0 flex-col gap-1.5 border border-border p-2',
              events.some((event) => event.type === 'HOLIDAY') && 'bg-warning-bg',
              dateKey === selectedKey && 'outline-2 -outline-offset-2 outline-brand',
            )}
          >
            <Button variant="ghost" size="inline"
              aria-label={dayAriaLabel(dateKey, todayKey, events)}
              aria-current={isToday ? 'date' : undefined}
              aria-pressed={dateKey === selectedKey}
              onClick={() => onSelectDay(dateKey)}
              className="flex items-center justify-start gap-1.5 rounded-control text-left focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-brand"
            >
              <span className="text-size-12 font-semibold text-ink-soft">{WEEKDAY_INITIALS[(parseDateKey(dateKey).getDay() + 6) % 7]}</span>
              <span
                className={cn(
                  'flex size-7 items-center justify-center rounded-full text-size-13 font-semibold tabular-nums',
                  isToday ? 'bg-brand text-on-accent' : 'text-ink',
                )}
              >
                {parseDateKey(dateKey).getDate()}
              </span>
            </Button>
            {events.length === 0 ? (
              <span className="text-size-12 text-ink-faint">Sin eventos</span>
            ) : (
              events.map((event) => <CalendarEventButton key={event.id} event={event} onSelectEvent={onSelectEvent} />)
            )}
          </div>
        );
      })}
    </div>
  );
}

export function CalendarWeekStrip({ days, eventsByDay, todayKey, selectedKey, onSelectDay }) {
  return (
    <div className="flex flex-col">
      <WeekdayHeader />
      <div className="grid grid-cols-7">
        {days.map((dateKey) => (
          <CalendarCompactDayCell
            key={dateKey}
            dateKey={dateKey}
            todayKey={todayKey}
            inMonth
            selected={dateKey === selectedKey}
            events={eventsByDay.get(dateKey) ?? []}
            onSelectDay={onSelectDay}
          />
        ))}
      </div>
    </div>
  );
}
