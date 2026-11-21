import { useEffect, useMemo, useState } from 'react';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { localIsoDate } from '@/lib/calendario';
import { useMyCalendar } from '@/features/workspace/hooks/useMyCalendar';
import {
  CALENDAR_EVENT_TYPES,
  countEventsByType,
  filterCalendarEvents,
  groupEventsByDay,
  institutionToday,
  monthKeyOf,
  monthRange,
  parseDateKey,
  shiftSelection,
  subjectNamesOf,
  weekDayKeys,
} from '@/features/workspace/lib/studentCalendar';

const TODAY_REFRESH_MS = 60_000;

export function useStudentCalendarState() {
  const isDesktop = useMediaQuery('(min-width: 768px)');
  const [today, setToday] = useState(institutionToday);
  const [view, setView] = useState(isDesktop ? 'month' : 'week');
  const [selectedDate, setSelectedDate] = useState(today);
  const [activeTypes, setActiveTypes] = useState(() => new Set(CALENDAR_EVENT_TYPES));
  const [subject, setSubject] = useState('');

  useEffect(() => {
    function refreshToday() {
      const next = institutionToday();
      setToday((current) => (localIsoDate(current) === localIsoDate(next) ? current : next));
    }
    const interval = setInterval(refreshToday, TODAY_REFRESH_MS);
    window.addEventListener('focus', refreshToday);
    document.addEventListener('visibilitychange', refreshToday);
    return () => {
      clearInterval(interval);
      window.removeEventListener('focus', refreshToday);
      document.removeEventListener('visibilitychange', refreshToday);
    };
  }, []);

  const monthKey = monthKeyOf(selectedDate);
  const range = useMemo(() => monthRange(parseDateKey(`${monthKey}-01`)), [monthKey]);
  const query = useMyCalendar(range.from, range.to);
  const isRangeLoading = query.isPlaceholderData;
  const isRangeReady = query.isSuccess && !isRangeLoading;
  const allEvents = useMemo(() => (isRangeLoading ? [] : (query.data?.events ?? [])), [isRangeLoading, query.data]);

  const subjects = useMemo(() => subjectNamesOf(allEvents), [allEvents]);
  const activeSubject = subjects.includes(subject) ? subject : '';

  const visibleEvents = useMemo(
    () => filterCalendarEvents(allEvents, { types: activeTypes, subject: activeSubject }),
    [allEvents, activeTypes, activeSubject],
  );
  const eventsByDay = useMemo(() => groupEventsByDay(visibleEvents), [visibleEvents]);
  const counts = useMemo(() => countEventsByType(allEvents, monthKey, activeSubject), [allEvents, monthKey, activeSubject]);

  const selectedKey = localIsoDate(selectedDate);
  const hasActiveFilter = activeTypes.size !== CALENDAR_EVENT_TYPES.length || activeSubject !== '';

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
    todayKey: localIsoDate(today),
    selectedDate,
    selectedKey,
    monthKey,
    monthDays: range.days.filter((key) => key.startsWith(monthKey)),
    gridDays: range.days,
    weekDays: weekDayKeys(selectedDate),
    query,
    isRangeLoading,
    generatedAt: isRangeReady ? (query.data?.generatedAt ?? null) : null,
    hasNoEnrollments: isRangeReady && query.data?.hasActiveEnrollments === false,
    eventsByDay,
    selectedDayEvents: eventsByDay.get(selectedKey) ?? [],
    counts,
    subjects,
    activeTypes,
    subject: activeSubject,
    hasActiveFilter,
    toggleType,
    setSubject,
    selectDay: (dateKey) => setSelectedDate(parseDateKey(dateKey)),
    goToToday: () => setSelectedDate(today),
    shift: (amount) => setSelectedDate((current) => shiftSelection(current, view, amount, today)),
  };
}
