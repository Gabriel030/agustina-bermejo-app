import { useMemo, useState } from 'react';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { useMyCalendar } from '@/features/workspace/hooks/useMyCalendar';
import {
  CALENDAR_EVENT_TYPES,
  countEventsByType,
  filterCalendarEvents,
  groupEventsByDay,
  hasOnlyHolidays,
  monthKeyOf,
  monthRange,
  parseDateKey,
  shiftSelection,
  subjectNamesOf,
  toDateKey,
  weekDayKeys,
} from '@/features/workspace/lib/studentCalendar';

export function useStudentCalendarState() {
  const isDesktop = useMediaQuery('(min-width: 768px)');
  const [today] = useState(() => new Date());
  const [view, setView] = useState(isDesktop ? 'month' : 'week');
  const [selectedDate, setSelectedDate] = useState(today);
  const [activeTypes, setActiveTypes] = useState(() => new Set(CALENDAR_EVENT_TYPES));
  const [subject, setSubject] = useState('');

  const monthKey = monthKeyOf(selectedDate);
  const range = useMemo(() => monthRange(parseDateKey(`${monthKey}-01`)), [monthKey]);
  const query = useMyCalendar(range.from, range.to);
  const allEvents = query.data?.events;

  const visibleEvents = useMemo(
    () => filterCalendarEvents(allEvents ?? [], { types: activeTypes, subject }),
    [allEvents, activeTypes, subject],
  );
  const eventsByDay = useMemo(() => groupEventsByDay(visibleEvents), [visibleEvents]);
  const counts = useMemo(() => countEventsByType(allEvents ?? [], monthKey, subject), [allEvents, monthKey, subject]);
  const subjects = useMemo(() => subjectNamesOf(allEvents ?? []), [allEvents]);

  const selectedKey = toDateKey(selectedDate);
  const hasActiveFilter = activeTypes.size !== CALENDAR_EVENT_TYPES.length || subject !== '';

  function toggleType(type) {
    setActiveTypes((current) => {
      const next = new Set(current);
      if (next.has(type)) next.delete(type);
      else next.add(type);
      return next;
    });
  }

  return {
    isDesktop,
    view,
    setView,
    today,
    todayKey: toDateKey(today),
    selectedDate,
    selectedKey,
    monthKey,
    monthDays: range.days.filter((key) => key.startsWith(monthKey)),
    gridDays: range.days,
    weekDays: weekDayKeys(selectedDate),
    query,
    generatedAt: query.data?.generatedAt ?? null,
    hasNoEnrollments: Boolean(allEvents) && hasOnlyHolidays(allEvents),
    eventsByDay,
    selectedDayEvents: eventsByDay.get(selectedKey) ?? [],
    counts,
    subjects,
    activeTypes,
    subject,
    hasActiveFilter,
    toggleType,
    setSubject,
    selectDay: (dateKey) => setSelectedDate(parseDateKey(dateKey)),
    goToToday: () => setSelectedDate(today),
    shift: (amount) => setSelectedDate((current) => shiftSelection(current, view, amount, today)),
  };
}
