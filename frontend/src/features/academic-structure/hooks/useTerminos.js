import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useTerminos() {
  return useQuery({
    queryKey: ['terminos'],
    queryFn: () => apiFetch('/terms?status=ACTIVE'),
  });
}
