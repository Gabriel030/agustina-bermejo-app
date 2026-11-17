import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const CONSENTS_KEY = ['me', 'contact-consents'];

export function useContactConsents() {
  return useQuery({ queryKey: CONSENTS_KEY, queryFn: () => apiFetch('/me/contact-consents') });
}

export function useUpdateContactConsent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (change) => apiFetch('/me/contact-consents', { method: 'PUT', body: JSON.stringify(change) }),
    onSuccess: (entry) => {
      queryClient.setQueryData(CONSENTS_KEY, (current) =>
        (current ?? []).map((item) =>
          item.channel === entry.channel && item.category === entry.category ? entry : item,
        ),
      );
      queryClient.invalidateQueries({ queryKey: CONSENTS_KEY, exact: true, refetchType: 'none' });
    },
  });
}
