import { useMesas } from '@/features/evaluation';
import { useTerminos } from '@/features/academic-structure';
import { useHolidays } from '@/features/academic-structure/hooks/useHolidays';
import { toCard } from '@/features/workspace/lib/homeCards';
import { upcomingEvents, upcomingWindow } from '@/features/workspace/lib/upcomingEvents';

const UPCOMING_CARD = { id: 'upcomingEvents', title: 'Próximos 30 días', icon: 'calendar' };

function withoutDuplicateHolidays(holidays) {
  return [...new Map(holidays.map((holiday) => [holiday.id, holiday])).values()];
}

function asLoadingWhenPending(query) {
  return query.isPending ? { ...query, isLoading: true } : query;
}

export function useUpcomingEvents() {
  const now = new Date();
  const { from, to } = upcomingWindow(now);
  const boards = useMesas('SCHEDULED');
  const holidaysOfStartYear = useHolidays(Number(from.slice(0, 4)));
  const holidaysOfEndYear = useHolidays(Number(to.slice(0, 4)));
  const terms = useTerminos();
  const sources = [boards, holidaysOfStartYear, holidaysOfEndYear, terms];

  return toCard(UPCOMING_CARD, sources.map(asLoadingWhenPending), () => {
    const events = upcomingEvents(
      {
        boards: boards.data ?? [],
        holidays: withoutDuplicateHolidays([...(holidaysOfStartYear.data ?? []), ...(holidaysOfEndYear.data ?? [])]),
        terms: terms.data ?? [],
      },
      now,
    );
    return { count: events.length, events };
  });
}
