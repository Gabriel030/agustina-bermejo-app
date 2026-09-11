import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useBillingStanding() {
  return useQuery({
    queryKey: ['me', 'billing-standing'],
    queryFn: () => apiFetch('/me/billing-standing'),
  });
}

export function useFacturasDeCurso(courseEnrollmentId) {
  return useQuery({
    queryKey: ['me', 'course-enrollments', courseEnrollmentId, 'invoices'],
    queryFn: () => apiFetch(`/me/course-enrollments/${courseEnrollmentId}/invoices`),
    enabled: !!courseEnrollmentId,
  });
}

export function useFacturasDeMatricula(programEnrollmentId) {
  return useQuery({
    queryKey: ['me', 'program-enrollments', programEnrollmentId, 'invoices'],
    queryFn: () => apiFetch(`/me/program-enrollments/${programEnrollmentId}/invoices`),
    enabled: !!programEnrollmentId,
  });
}

export function useMisComprobantes() {
  return useQuery({
    queryKey: ['me', 'payment-receipts'],
    queryFn: () => apiFetch('/me/payment-receipts'),
  });
}

export function useSubirComprobante() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ invoiceId, file }) => {
      const body = new FormData();
      body.append('comprobante', file);
      return apiFetch(`/me/invoices/${invoiceId}/payment-receipts`, { method: 'POST', body });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me', 'payment-receipts'] });
    },
  });
}

export function useCrearPreferenciaMp() {
  return useMutation({
    mutationFn: (invoiceId) =>
      apiFetch(`/me/invoices/${invoiceId}/mp-preference`, { method: 'POST' }),
  });
}

export function usePaymentConfig() {
  return useQuery({
    queryKey: ['payment-config'],
    queryFn: () => apiFetch('/payment-config'),
  });
}
