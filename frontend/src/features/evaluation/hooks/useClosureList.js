import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { closureListQuery } from '../lib/closureList';

export function useClosureList(filters) {
  return useQuery({
    queryKey: ['commission-closures', filters],
    queryFn: () => apiFetch(`/commission-closures?${closureListQuery(filters)}`),
    placeholderData: keepPreviousData,
  });
}

export function useClosureTerms() {
  return useQuery({ queryKey: ['terms', 'closure-list'], queryFn: () => apiFetch('/terms') });
}
