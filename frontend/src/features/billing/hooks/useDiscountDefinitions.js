import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useDiscountDefinitions() {
  return useQuery({
    queryKey: ['discount-definitions', 'list'],
    queryFn: () => apiFetch('/discount-definitions'),
  });
}

export function useDiscountDefinition(id) {
  return useQuery({
    queryKey: ['discount-definitions', 'detail', id],
    queryFn: () => apiFetch(`/discount-definitions/${id}`),
    enabled: !!id,
    retry: false,
  });
}

export function useDiscountDefinitionValidation(payload) {
  return useQuery({
    queryKey: ['discount-definitions', 'validation', payload],
    queryFn: () => apiFetch('/discount-definitions/validation', { method: 'POST', body: JSON.stringify(payload) }),
    enabled: !!payload,
    placeholderData: keepPreviousData,
    retry: false,
  });
}

export function useCreateDiscountDefinition() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) => apiFetch('/discount-definitions', { method: 'POST', body: JSON.stringify(payload) }),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['discount-definitions'] });
      queryClient.invalidateQueries({ queryKey: ['authorization-requests'] });
    },
  });
}

export function useCloseDiscountDefinition() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }) =>
      apiFetch(`/discount-definitions/${id}/closure`, { method: 'POST', body: JSON.stringify({ reason }) }),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['discount-definitions'] });
      queryClient.invalidateQueries({ queryKey: ['authorization-requests'] });
    },
  });
}
