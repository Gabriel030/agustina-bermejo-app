import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const PENDING_KEY = ['attendance', 'justifications', 'PENDING'];

export const JUSTIFICATIONS_PAGE_SIZE = 100;

export function useJustificacionesPendientes(size = JUSTIFICATIONS_PAGE_SIZE) {
  return useQuery({
    queryKey: [...PENDING_KEY, size],
    queryFn: () => apiFetch(`/attendance/justifications?status=PENDING&page=0&size=${size}`),
    placeholderData: keepPreviousData,
  });
}

export function useAprobarJustificacion() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (attendanceId) => apiFetch(`/attendance/${attendanceId}/approve-justification`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: PENDING_KEY }),
  });
}

export function useRechazarJustificacion() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ attendanceId, reason }) =>
      apiFetch(`/attendance/${attendanceId}/reject-justification`, { method: 'POST', body: JSON.stringify({ reason }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: PENDING_KEY }),
  });
}
