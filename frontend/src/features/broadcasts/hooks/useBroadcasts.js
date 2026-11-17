import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const DEBOUNCE_MS = 300;

export function useSegmentCatalog() {
  return useQuery({ queryKey: ['broadcasts', 'segment-catalog'], queryFn: () => apiFetch('/broadcasts/segment-catalog') });
}

export function useAudiencePreview(params) {
  const serialized = JSON.stringify(params);
  const [debounced, setDebounced] = useState(serialized);

  useEffect(() => {
    const id = setTimeout(() => setDebounced(serialized), DEBOUNCE_MS);
    return () => clearTimeout(id);
  }, [serialized]);

  const query = useQuery({
    queryKey: ['broadcasts', 'audience-preview', debounced],
    queryFn: () => apiFetch('/broadcasts/audience-preview', { method: 'POST', body: debounced }),
    placeholderData: (previous) => previous,
  });
  return { ...query, recalculating: serialized !== debounced || query.isFetching };
}

export function useCreateBroadcast() {
  return useMutation({
    mutationFn: (payload) => apiFetch('/broadcasts', { method: 'POST', body: JSON.stringify(payload) }),
  });
}

export function useConfirmBroadcast() {
  return useMutation({
    mutationFn: (id) => apiFetch(`/broadcasts/${id}/confirm`, { method: 'POST' }),
  });
}

export function useMessageTemplates(channel) {
  return useQuery({
    queryKey: ['message-templates', { channel }],
    queryFn: () => apiFetch(`/message-templates?channel=${channel}`),
  });
}

export function useSaveMessageTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...payload }) =>
      apiFetch(id ? `/message-templates/${id}` : '/message-templates', {
        method: id ? 'PUT' : 'POST',
        body: JSON.stringify(payload),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['message-templates'] }),
  });
}

export function useRenderPreview({ channel, subject, bodyText, personId }) {
  const serialized = JSON.stringify({ channel, subject, bodyText, personId });
  const [debounced, setDebounced] = useState(serialized);

  useEffect(() => {
    const id = setTimeout(() => setDebounced(serialized), DEBOUNCE_MS);
    return () => clearTimeout(id);
  }, [serialized]);

  return useQuery({
    queryKey: ['message-templates', 'render-preview', debounced],
    queryFn: () => apiFetch('/message-templates/render-preview', { method: 'POST', body: debounced }),
    enabled: personId != null && bodyText.trim() !== '',
    retry: false,
  });
}
