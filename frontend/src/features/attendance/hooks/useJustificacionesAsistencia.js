import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const PENDING_KEY = ['attendance', 'justifications', 'PENDING'];

export function useJustificacionesPendientes() {
  return useQuery({
    queryKey: PENDING_KEY,
    queryFn: () => apiFetch('/attendance/justifications?status=PENDING&page=0&size=100'),
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
