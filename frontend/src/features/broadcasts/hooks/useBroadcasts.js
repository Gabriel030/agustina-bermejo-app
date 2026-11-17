import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useBroadcastList(status, page) {
  const params = new URLSearchParams({ page: String(page - 1), size: '20' });
  if (status) params.set('status', status);
  return useQuery({
    queryKey: ['broadcasts', { status: status ?? null, page }],
    queryFn: () => apiFetch(`/broadcasts?${params}`),
    placeholderData: (previous) => previous,
  });
}

export function useBroadcast(broadcastId) {
  return useQuery({
    queryKey: ['broadcasts', broadcastId],
    queryFn: () => apiFetch(`/broadcasts/${broadcastId}`),
    enabled: !!broadcastId,
  });
}

export function useBroadcastRecipients(broadcastId, page) {
  return useQuery({
    queryKey: ['broadcasts', broadcastId, 'recipients', page],
    queryFn: () => apiFetch(`/broadcasts/${broadcastId}/recipients?page=${page - 1}&size=50`),
    enabled: !!broadcastId,
    placeholderData: (previous) => previous,
  });
}

export function useCancelBroadcast(broadcastId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (reason) => apiFetch(`/broadcasts/${broadcastId}/cancel`, { method: 'POST', body: JSON.stringify({ reason }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['broadcasts'] }),
  });
}
