import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

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
    mutationFn: (payload) =>
      apiFetch(`/students/${studentId}/refund-requests`, { method: 'POST', body: JSON.stringify(payload) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['students'] }),
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
