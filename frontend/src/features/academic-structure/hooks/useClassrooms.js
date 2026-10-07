import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useClassrooms() {
  return useQuery({
    queryKey: ['classrooms'],
    queryFn: () => apiFetch('/classrooms'),
  });
}

export function useClassroomsAvailability({ termId, dayOfWeek, startTime, endTime }) {
  const enabled = !!(termId && dayOfWeek && startTime && endTime);
  const params = new URLSearchParams({ dayOfWeek, startTime, endTime });
  return useQuery({
    queryKey: ['classrooms-availability', termId, dayOfWeek, startTime, endTime],
    queryFn: () => apiFetch(`/terms/${termId}/classrooms/availability?${params}`),
    enabled,
  });
}
