import { EmptyState } from '@/components/EmptyState';
import { CalendarAgendaRow } from '@/features/workspace/components/calendar/CalendarAgendaRow';

export function CalendarDayAgenda({ events, onSelectEvent }) {
  if (events.length === 0) {
    return <EmptyState icon="🗓️" title="Sin eventos este día" description="Elegí otro día o revisá los filtros." className="py-6" />;
  }
  return (
    <ul className="flex flex-col gap-2">
      {events.map((event) => (
        <li key={event.id}>
          <CalendarAgendaRow event={event} onSelectEvent={onSelectEvent} />
        </li>
      ))}
    </ul>
  );
}
