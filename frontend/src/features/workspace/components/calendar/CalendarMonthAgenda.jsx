import { EmptyState } from '@/components/EmptyState';
import { CalendarAgendaRow } from '@/features/workspace/components/calendar/CalendarAgendaRow';
import { formatAgendaDayTitle } from '@/features/workspace/lib/studentCalendar';

export function CalendarMonthAgenda({ monthDays, eventsByDay, todayKey, onSelectEvent }) {
  const daysWithEvents = monthDays.filter((key) => eventsByDay.has(key));
  if (daysWithEvents.length === 0) {
    return <EmptyState icon="🗓️" title="Sin eventos este mes" description="Probá con otro mes o revisá los filtros." />;
  }
  return (
    <div className="flex flex-col gap-4">
      {daysWithEvents.map((key) => (
        <section key={key} aria-labelledby={`agenda-day-${key}`} className="flex flex-col gap-2">
          <h3 id={`agenda-day-${key}`} className="font-heading text-size-14 font-semibold first-letter:uppercase text-ink">
            {formatAgendaDayTitle(key, todayKey)}
          </h3>
          <ul className="flex flex-col gap-2">
            {eventsByDay.get(key).map((event) => (
              <li key={event.id}>
                <CalendarAgendaRow event={event} onSelectEvent={onSelectEvent} />
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
