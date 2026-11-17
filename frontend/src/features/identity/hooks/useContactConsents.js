import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const CONSENTS_KEY = ['me', 'contact-consents'];

export function useContactConsents({ enabled = true } = {}) {
  return useQuery({ queryKey: CONSENTS_KEY, queryFn: () => apiFetch('/me/contact-consents'), enabled });
}

export function useUpdateContactConsent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (change) => apiFetch('/me/contact-consents', { method: 'PUT', body: JSON.stringify({ source: 'PROFILE', ...change }) }),
    onSuccess: (entry) => {
      queryClient.setQueryData(CONSENTS_KEY, (current) => {
        const entries = current ?? [];
        const isSame = (item) => item.channel === entry.channel && item.category === entry.category;
        return entries.some(isSame) ? entries.map((item) => (isSame(item) ? entry : item)) : [...entries, entry];
      });
      queryClient.invalidateQueries({ queryKey: CONSENTS_KEY, exact: true, refetchType: 'none' });
    },
  });
}
