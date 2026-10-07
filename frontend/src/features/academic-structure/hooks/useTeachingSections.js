import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useComisionesDictadas(habilitado = true) {
  return useQuery({
    queryKey: ['me', 'teaching-sections'],
    queryFn: () => apiFetch('/me/teaching-sections'),
    enabled: habilitado,
  });
}
