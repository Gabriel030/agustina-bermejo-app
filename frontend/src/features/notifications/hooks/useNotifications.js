import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { apiFetch } from '@/api/client';
import { offlineQuery } from '@/api/offlineQuery';
import { POLL_INTERVAL, STALE_TIME, pollingOptions } from '@/api/cachePolicy';

export function useMisNotificaciones() {
  return useQuery(
    offlineQuery('N0', {
      queryKey: ['me', 'notifications'],
      queryFn: () => apiFetch('/me/notifications'),
      ...pollingOptions(POLL_INTERVAL.NOTIFICATIONS, STALE_TIME.OPERATIONAL),
    }),
  );
}

export function useMarcarNotificacionLeida() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/me/notifications/${id}/read`, { method: 'POST' }),
    onSuccess: (_data, id) => {
      queryClient.setQueryData(['me', 'notifications'], (lista) =>
        lista?.map((notificacion) => (notificacion.id === id ? { ...notificacion, read: true } : notificacion)),
      );
      queryClient.invalidateQueries({ queryKey: ['me', 'notifications'], refetchType: 'none' });
    },
    onError: () => {
      queryClient.invalidateQueries({ queryKey: ['me', 'notifications'] });
      toast.error('No pudimos marcar la notificación como leída. Probá de nuevo.');
    },
  });
}
