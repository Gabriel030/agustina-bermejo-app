import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

function post(path, payload) {
  return apiFetch(path, { method: 'POST', body: JSON.stringify(payload) });
}

export function usePrices() {
  return useQuery({ queryKey: ['prices'], queryFn: () => apiFetch('/prices') });
}

export function usePriceHistory(priceId) {
  return useQuery({
    queryKey: ['prices', priceId, 'versions'],
    queryFn: () => apiFetch(`/prices/${priceId}/versions`),
    enabled: priceId != null,
  });
}

export function usePriceImpact(priceId) {
  return useMutation({ mutationFn: (payload) => post(`/prices/${priceId}/versions/impact`, payload) });
}

export function usePriceValidation(priceId) {
  return useMutation({ mutationFn: (payload) => post(`/prices/${priceId}/versions/validation`, payload) });
}

export function usePublishPriceVersion(priceId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) => post(`/prices/${priceId}/versions`, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['prices'] });
      queryClient.invalidateQueries({ queryKey: ['authorization-requests'] });
    },
  });
}

export function useCancelPriceVersion() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ versionId, reason }) => post(`/price-versions/${versionId}/cancellation`, { reason }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['prices'] }),
  });
}
