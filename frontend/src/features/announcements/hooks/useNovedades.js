import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { offlineQuery } from '@/api/offlineQuery';
import { POLL_INTERVAL, STALE_TIME, pollingOptions } from '@/api/cachePolicy';

export function useNovedades() {
  return useQuery(
    offlineQuery('N0', {
      queryKey: ['me', 'announcements'],
      queryFn: () => apiFetch('/me/announcements'),
      ...pollingOptions(POLL_INTERVAL.ANNOUNCEMENTS, STALE_TIME.MEDIUM),
    }),
  );
}
