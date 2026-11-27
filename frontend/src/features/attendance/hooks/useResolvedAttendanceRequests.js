import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export const RESOLVED_PAGE_SIZE = 20;

const RESOLVED_KEY = ['attendance', 'change-requests', 'resolved'];

export function useResolvedAttendanceRequests({ result, size = RESOLVED_PAGE_SIZE }) {
  return useQuery({
    queryKey: [...RESOLVED_KEY, result ?? 'ALL', size],
    queryFn: () => {
      const params = new URLSearchParams({ page: '0', size: String(size) });
      if (result) params.set('result', result);
      return apiFetch(`/attendance/change-requests/resolved?${params}`);
    },
    placeholderData: keepPreviousData,
  });
}

export function useRevertAttendanceDecision() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }) =>
      apiFetch(`/attendance/change-requests/${id}/revert`, { method: 'POST', body: JSON.stringify({ reason }) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['attendance', 'change-requests'] }),
  });
}
