import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const ROOT = 'waitlists';
export const WAITLIST_PAGE_SIZE = 20;
export const WAITLIST_HISTORY_PAGE_SIZE = 20;

export function useWaitlists({ kind, expiringWithinDays, page }) {
  const params = new URLSearchParams({ page: String(page), size: String(WAITLIST_PAGE_SIZE) });
  if (kind && kind !== 'ALL') params.set('kind', kind);
  if (expiringWithinDays) params.set('expiringWithinDays', String(expiringWithinDays));
  return useQuery({
    queryKey: [ROOT, 'list', { kind: kind ?? 'ALL', expiringWithinDays: expiringWithinDays ?? null, page }],
    queryFn: () => apiFetch(`/waitlists?${params.toString()}`),
    placeholderData: keepPreviousData,
  });
}

export function useWaitlistQueue(kind, id) {
  return useQuery({
    queryKey: [ROOT, 'queue', kind, id],
    queryFn: () => apiFetch(`/waitlists/${kind}/${id}`),
    enabled: !!kind && !!id,
  });
}

export function useWaitlistHistory(kind, id, page, enabled) {
  const params = new URLSearchParams({ page: String(page), size: String(WAITLIST_HISTORY_PAGE_SIZE) });
  return useQuery({
    queryKey: [ROOT, 'history', kind, id, page],
    queryFn: () => apiFetch(`/waitlists/${kind}/${id}/history?${params.toString()}`),
    enabled: !!kind && !!id && enabled,
    placeholderData: keepPreviousData,
  });
}

export function useWaitlistEntryAction(kind, enrollmentId, action) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (reason) =>
      apiFetch(`/waitlists/${kind}/entries/${enrollmentId}/${action}`, {
        method: 'POST',
        body: action === 'promote' ? undefined : JSON.stringify({ reason }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [ROOT] }),
  });
}
