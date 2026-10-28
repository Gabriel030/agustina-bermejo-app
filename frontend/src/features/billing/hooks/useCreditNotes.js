import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

function post(path, payload) {
  return apiFetch(path, { method: 'POST', body: JSON.stringify(payload) });
}

export function useStudentCreditNotes(studentId) {
  return useQuery({
    queryKey: ['students', studentId, 'credit-notes'],
    queryFn: () => apiFetch(`/students/${studentId}/credit-notes`),
    enabled: !!studentId,
  });
}

export function useStudentCreditBalance(studentId) {
  return useQuery({
    queryKey: ['students', studentId, 'credit-balance'],
    queryFn: () => apiFetch(`/students/${studentId}/credit-balance`),
    enabled: !!studentId,
  });
}

export function useMyCreditNotes() {
  return useQuery({
    queryKey: ['me', 'credit-notes'],
    queryFn: () => apiFetch('/me/credit-notes'),
  });
}

export function useMyCreditBalance() {
  return useQuery({
    queryKey: ['me', 'credit-balance'],
    queryFn: () => apiFetch('/me/credit-balance'),
  });
}

export function useCancellationPreview(invoiceId, payload, enabled) {
  return useQuery({
    queryKey: ['invoices', invoiceId, 'cancellation-preview', payload],
    queryFn: () => post(`/invoices/${invoiceId}/cancellations/preview`, payload),
    enabled: enabled && !!invoiceId && !!payload,
    placeholderData: keepPreviousData,
    retry: false,
  });
}

function useInvalidatingMutation(mutationFn) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['students'] });
      queryClient.invalidateQueries({ queryKey: ['authorization-requests'] });
    },
  });
}

export function useCancelInvoice() {
  return useInvalidatingMutation(({ invoiceId, payload }) => post(`/invoices/${invoiceId}/cancellations`, payload));
}

export function useIssueCreditNote() {
  return useInvalidatingMutation(({ invoiceId, payload }) => post(`/invoices/${invoiceId}/credit-notes`, payload));
}

export function useStudentHeader(studentId) {
  return useQuery({
    queryKey: ['alumno', studentId],
    queryFn: () => apiFetch(`/students/${studentId}`),
    enabled: !!studentId,
  });
}
