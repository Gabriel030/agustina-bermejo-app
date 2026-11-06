import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { offlineQuery } from '@/api/offlineQuery';

export function useNovedades() {
  return useQuery(
    offlineQuery('N0', {
      queryKey: ['me', 'announcements'],
      queryFn: () => apiFetch('/me/announcements'),
      staleTime: 60_000,
      refetchInterval: () => (navigator.onLine ? 60_000 : false),
    }),
  );
}
