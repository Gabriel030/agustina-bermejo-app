import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiFetchBlob } from '@/api/client';
import { STALE_TIME } from '@/api/cachePolicy';

const FEED_KEY = ['me', 'calendar-feed'];
const ICS_FILENAME = 'calendario.ics';

export function useCalendarFeed() {
  return useQuery({
    queryKey: FEED_KEY,
    queryFn: () => apiFetch('/me/calendar/feed'),
    staleTime: STALE_TIME.OPERATIONAL,
    gcTime: 0,
  });
}

export function useIssueCalendarFeedToken({ onIssued }) {
  const queryClient = useQueryClient();
  return useMutation({
    gcTime: 0,
    mutationFn: async () => {
      const issued = await apiFetch('/me/calendar/feed/token', { method: 'POST' });
      onIssued(issued.url);
      return { createdAt: issued.createdAt };
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: FEED_KEY }),
  });
}

export function useRevokeCalendarFeedToken() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch('/me/calendar/feed/token', { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: FEED_KEY }),
  });
}

export function useDownloadCalendarIcs() {
  return useMutation({
    mutationFn: async () => {
      const { blob } = await apiFetchBlob('/me/calendar/export.ics');
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = ICS_FILENAME;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    },
  });
}
