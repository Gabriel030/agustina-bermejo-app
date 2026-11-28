import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export const OWN_WAITLIST_POSITIONS_KEY = ['me', 'waitlist-positions'];

export function useOwnWaitlistPositions({ enabled = true } = {}) {
  return useQuery({
    queryKey: OWN_WAITLIST_POSITIONS_KEY,
    queryFn: () => apiFetch('/me/waitlist-positions'),
    enabled,
  });
}
