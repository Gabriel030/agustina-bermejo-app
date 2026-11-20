import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { apiFetch } from '@/api/client';
import { offlineQuery } from '@/api/offlineQuery';
import { POLL_INTERVAL, STALE_TIME, pollingOptions } from '@/api/cachePolicy';

export const NOTIFICATIONS_PAGE_SIZE = 10;

export function useMisNotificaciones({ page = 0, enabled = true } = {}) {
  return useQuery(
    offlineQuery('N0', {
      queryKey: ['me', 'notifications', { page, size: NOTIFICATIONS_PAGE_SIZE }],
      queryFn: () => apiFetch(`/me/notifications?page=${page}&size=${NOTIFICATIONS_PAGE_SIZE}`),
      placeholderData: (previous) => previous,
      enabled,
      ...pollingOptions(POLL_INTERVAL.NOTIFICATIONS, STALE_TIME.OPERATIONAL),
    }),
  );
}

export function useNotificacionesNoLeidas() {
  return useQuery(
    offlineQuery('N0', {
      queryKey: ['me', 'notifications', 'unread-count'],
      queryFn: () => apiFetch('/me/notifications/unread-count'),
      ...pollingOptions(POLL_INTERVAL.NOTIFICATIONS, STALE_TIME.OPERATIONAL),
    }),
  );
}

export function useMarcarNotificacionLeida() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/me/notifications/${id}/read`, { method: 'POST' }),
    onSuccess: (_data, id) => {
      queryClient.setQueriesData({ queryKey: ['me', 'notifications'] }, (cached) => {
        if (!cached?.items) return cached;
        return { ...cached, items: cached.items.map((notificacion) => (notificacion.id === id ? { ...notificacion, read: true } : notificacion)) };
      });
      queryClient.setQueryData(['me', 'notifications', 'unread-count'], (cached) =>
        cached ? { count: Math.max(0, cached.count - 1) } : cached,
      );
      queryClient.invalidateQueries({ queryKey: ['me', 'notifications'], refetchType: 'none' });
    },
    onError: () => {
      queryClient.invalidateQueries({ queryKey: ['me', 'notifications'] });
      toast.error('No pudimos marcar la notificación como leída. Probá de nuevo.');
    },
  });
}
