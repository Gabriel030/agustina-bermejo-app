import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

function post(path, payload) {
  return apiFetch(path, { method: 'POST', body: payload ? JSON.stringify(payload) : undefined });
}

export function useStudentPayments(studentId) {
  return useQuery({
    queryKey: ['students', studentId, 'payments'],
    queryFn: () => apiFetch(`/students/${studentId}/payments`),
    enabled: !!studentId,
  });
}

export function useChargebackPreview(paymentId, payload, enabled) {
  return useQuery({
    queryKey: ['payments', paymentId, 'chargeback-preview', payload],
    queryFn: () => post(`/payments/${paymentId}/chargebacks/preview`, payload),
    enabled: enabled && !!payload,
    placeholderData: keepPreviousData,
    retry: false,
  });
}

export function useChargebackReversalPreview(chargebackId, enabled) {
  return useQuery({
    queryKey: ['chargebacks', chargebackId, 'reversal-preview'],
    queryFn: () => post(`/chargebacks/${chargebackId}/reversal/preview`),
    enabled: enabled && !!chargebackId,
    retry: false,
  });
}

export function useApprovalRevocationPreview(paymentId, payload, enabled) {
  return useQuery({
    queryKey: ['payments', paymentId, 'revocation-preview', payload],
    queryFn: () => post(`/payments/${paymentId}/approval-revocation/preview`, payload),
    enabled: enabled && !!payload,
    placeholderData: keepPreviousData,
    retry: false,
  });
}

export function useRegisterChargeback() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ paymentId, payload }) => post(`/payments/${paymentId}/chargebacks`, payload),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['students'] }),
  });
}

export function useReverseChargeback() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ chargebackId }) => post(`/chargebacks/${chargebackId}/reversal`),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['students'] }),
  });
}

export function useRevokeApproval() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ paymentId, payload }) => post(`/payments/${paymentId}/approval-revocation`, payload),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['students'] }),
  });
}
