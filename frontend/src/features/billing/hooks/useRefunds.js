import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useStudentRefunds(studentId) {
  return useQuery({
    queryKey: ['students', studentId, 'refunds'],
    queryFn: () => apiFetch(`/students/${studentId}/refunds`),
    enabled: !!studentId,
  });
}

export function useCompleteRefund() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ refundId, payload }) =>
      apiFetch(`/refunds/${refundId}/completion`, { method: 'POST', body: JSON.stringify(payload) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['students'] }),
  });
}

export function useFailRefund() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ refundId, reason }) =>
      apiFetch(`/refunds/${refundId}/failure`, { method: 'POST', body: JSON.stringify({ reason }) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['students'] }),
  });
}
