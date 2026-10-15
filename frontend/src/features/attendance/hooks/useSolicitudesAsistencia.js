import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useSolicitudesPendientes() {
  return useQuery({
    queryKey: ['attendance', 'change-requests', 'PENDING'],
    queryFn: () => apiFetch('/attendance/change-requests?status=PENDING'),
  });
}

export function useAprobarSolicitud() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/attendance/change-requests/${id}/approve`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['attendance', 'change-requests', 'PENDING'] }),
  });
}

export function useRechazarSolicitud() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/attendance/change-requests/${id}/reject`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['attendance', 'change-requests', 'PENDING'] }),
  });
}
