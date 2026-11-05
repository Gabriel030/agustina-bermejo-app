import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { apiFetch } from '@/api/client';

export function useMisNotificaciones() {
  return useQuery({
    queryKey: ['me', 'notifications'],
    queryFn: () => apiFetch('/me/notifications'),
    refetchInterval: () => (navigator.onLine ? 15_000 : false),
  });
}

export function useMarcarNotificacionLeida() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/me/notifications/${id}/read`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'notifications'] }),
    onError: () => toast.error('No pudimos marcar la notificación como leída. Probá de nuevo.'),
  });
}
