import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useLegajoPersonal(personId, enabled = true) {
  return useQuery({
    queryKey: ['personal', personId],
    queryFn: () => apiFetch(`/staff/${personId}`),
    enabled: !!personId && enabled,
  });
}
