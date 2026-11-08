import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { invalidateRefundAffectedQueries } from './studentQueryKeys';

export function useStudentRefunds(studentId) {
  return useQuery({
    queryKey: ['students', studentId, 'refunds'],
    queryFn: () => apiFetch(`/students/${studentId}/refunds`),
    enabled: !!studentId,
  });
}

export function useRefundEligibility(studentId) {
  return useQuery({
    queryKey: ['students', studentId, 'refund-eligibility'],
    queryFn: () => apiFetch(`/students/${studentId}/refund-eligibility`),
    enabled: !!studentId,
  });
}

export function useRefundPreview(studentId, payload) {
  return useQuery({
    queryKey: ['students', studentId, 'refund-preview', payload],
    queryFn: () =>
      apiFetch(`/students/${studentId}/refund-requests/preview`, { method: 'POST', body: JSON.stringify(payload) }),
    enabled: !!studentId && !!payload,
    placeholderData: keepPreviousData,
    retry: false,
  });
}

export function useRequestRefund(studentId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ payload, idempotencyKey }) =>
      apiFetch(`/students/${studentId}/refund-requests`, {
        method: 'POST',
        body: JSON.stringify(payload),
        headers: { 'Idempotency-Key': idempotencyKey },
      }),
    onSettled: () => invalidateRefundAffectedQueries(queryClient, studentId),
  });
}

export function useCompleteRefund() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ refundId, payload }) =>
      apiFetch(`/refunds/${refundId}/completion`, { method: 'POST', body: JSON.stringify(payload) }),
    onSettled: (_data, _error, { studentId }) => invalidateRefundAffectedQueries(queryClient, studentId),
  });
}

export function useFailRefund() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ refundId, reason }) =>
      apiFetch(`/refunds/${refundId}/failure`, { method: 'POST', body: JSON.stringify({ reason }) }),
    onSettled: (_data, _error, { studentId }) => invalidateRefundAffectedQueries(queryClient, studentId),
  });
}
