import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useClassrooms() {
  return useQuery({
    queryKey: ['classrooms'],
    queryFn: () => apiFetch('/classrooms'),
  });
}

export function useClassroomsAvailability({ termId, dayOfWeek, startTime, endTime, seats }) {
  const enabled = !!(termId && dayOfWeek && startTime && endTime);
  const params = new URLSearchParams({ dayOfWeek, startTime, endTime });
  if (seats) params.set('seats', seats);
  return useQuery({
    queryKey: ['classrooms-availability', termId, dayOfWeek, startTime, endTime, seats ?? null],
    queryFn: () => apiFetch(`/terms/${termId}/classrooms/availability?${params}`),
    enabled,
  });
}

export function useClassroomOccupancy({ classroomId, termId, from, to }) {
  const params = new URLSearchParams({ termId });
  if (from) params.set('from', from);
  if (to) params.set('to', to);
  return useQuery({
    queryKey: ['classroom-occupancy', classroomId, termId, from ?? null, to ?? null],
    queryFn: () => apiFetch(`/classrooms/${classroomId}/occupancy?${params}`),
    enabled: !!(classroomId && termId),
  });
}
