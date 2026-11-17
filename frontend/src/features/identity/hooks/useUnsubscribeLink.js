import { useMutation, useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useUnsubscribeLink(token) {
  return useQuery({
    queryKey: ['public', 'unsubscribe', token],
    enabled: Boolean(token),
    retry: false,
    queryFn: () => apiFetch(`/public/unsubscribe/${encodeURIComponent(token)}`),
  });
}

export function useConfirmUnsubscribe(token) {
  return useMutation({
    mutationFn: () => apiFetch(`/public/unsubscribe/${encodeURIComponent(token)}`, { method: 'POST' }),
  });
}
