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

export function usePriceBreakdown(invoiceId) {
  return useQuery({
    queryKey: ['me', 'invoices', invoiceId, 'price-breakdown'],
    queryFn: () => apiFetch(`/me/invoices/${invoiceId}/price-breakdown`),
    enabled: !!invoiceId,
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

export function useAdminBillingOverview(studentId) {
  return useQuery({
    queryKey: ['students', studentId, 'billing-overview'],
    queryFn: () => apiFetch(`/students/${studentId}/billing-overview`),
    enabled: !!studentId,
  });
}

export function useRegistrarPago() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) => apiFetch('/payments', { method: 'POST', body: JSON.stringify(payload) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['students'] });
    },
  });
}

export function useComprobantesAdmin() {
  return useQuery({
    queryKey: ['admin', 'payment-receipts'],
    queryFn: () => apiFetch('/payment-receipts'),
  });
}

export function useAprobarComprobante() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/payment-receipts/${id}/approve`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'payment-receipts'] });
      queryClient.invalidateQueries({ queryKey: ['students'] });
    },
  });
}

export function useRechazarComprobante() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, rejectionReason }) =>
      apiFetch(`/payment-receipts/${id}/reject`, { method: 'POST', body: JSON.stringify({ rejectionReason }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'payment-receipts'] }),
  });
}
