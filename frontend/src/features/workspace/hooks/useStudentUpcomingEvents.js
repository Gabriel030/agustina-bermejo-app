import { useMyCalendar } from '@/features/workspace/hooks/useMyCalendar';
import { toCard } from '@/features/workspace/lib/homeCards';
import { upcomingWindow } from '@/features/workspace/lib/upcomingEvents';
import { studentUpcomingEvents } from '@/features/workspace/lib/studentUpcomingEvents';

const UPCOMING_CARD = { id: 'upcomingEvents', title: 'Próximos eventos', icon: 'calendar' };

export function useStudentUpcomingEvents() {
  const now = new Date();
  const { from, to } = upcomingWindow(now);
  const calendar = useMyCalendar(from, to);
  const source = calendar.isPending ? { ...calendar, isLoading: true } : calendar;

  return toCard(UPCOMING_CARD, [source], () => ({ events: studentUpcomingEvents(calendar.data?.events ?? [], now) }));
}
