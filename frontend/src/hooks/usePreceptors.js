import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function usePreceptors() {
  return useQuery({
    queryKey: ['preceptors'],
    queryFn: () => apiFetch('/staff?role=PRECEPTOR&status=ACTIVE'),
    staleTime: 60_000,
  });
}
