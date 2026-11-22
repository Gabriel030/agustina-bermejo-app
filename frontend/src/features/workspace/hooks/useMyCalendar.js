import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { STALE_TIME } from '@/api/cachePolicy';

export function toCalendarView(response) {
  return {
    generatedAt: response.generatedAt,
    hasActiveEnrollments: response.hasActiveEnrollments !== false,
    events: (response.events ?? []).map((event) => ({
      id: event.id,
      type: event.type,
      date: event.date,
      startTime: event.startTime ?? null,
      endTime: event.endTime ?? null,
      title: event.title,
      subjectName: event.subjectName ?? null,
      sectionName: event.sectionName ?? null,
      place: event.place ?? null,
      status: event.status,
      reason: event.reason ?? null,
      link: event.link ?? null,
    })),
  };
}

export function useMyCalendar(from, to) {
  return useQuery({
    queryKey: ['me', 'calendar', from, to],
    queryFn: async () => toCalendarView(await apiFetch(`/me/calendar?from=${from}&to=${to}`)),
    staleTime: STALE_TIME.MEDIUM,
    refetchOnWindowFocus: true,
    placeholderData: keepPreviousData,
  });
}
