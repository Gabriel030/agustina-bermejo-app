import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useNovedades() {
  return useQuery({
    queryKey: ['me', 'announcements'],
    queryFn: () => apiFetch('/me/announcements'),
    staleTime: 60_000,
    refetchInterval: 60_000,
  });
}
