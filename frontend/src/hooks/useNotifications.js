import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useMisNotificaciones() {
  return useQuery({
    queryKey: ['me', 'notifications'],
    queryFn: () => apiFetch('/me/notifications'),
    refetchInterval: 15_000,
  });
}

export function useMarcarNotificacionLeida() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/me/notifications/${id}/read`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'notifications'] }),
  });
}
