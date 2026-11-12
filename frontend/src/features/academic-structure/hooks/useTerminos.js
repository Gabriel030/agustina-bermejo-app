import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { offlineQuery } from '@/api/offlineQuery';

export function useTerminos() {
  return useQuery(
    offlineQuery('N0c', {
      queryKey: ['terminos'],
      queryFn: () => apiFetch('/terms?status=ACTIVE'),
    }),
  );
}

export function useTerm(termId) {
  return useQuery({ queryKey: ['terms', termId], queryFn: () => apiFetch(`/terms/${termId}`), enabled: !!termId });
}
