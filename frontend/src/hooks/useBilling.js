import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useEnrollmentPaymentStatus() {
  return useQuery({
    queryKey: ['me', 'enrollment-payment-status'],
    queryFn: () => apiFetch('/me/enrollment-payment-status'),
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
    queryKey: ['me', 'bank-transfer-receipts'],
    queryFn: () => apiFetch('/me/bank-transfer-receipts'),
  });
}

export function usePriceBreakdown(invoiceId) {
  return useQuery({
    queryKey: ['me', 'invoices', invoiceId, 'price-breakdown'],
    queryFn: () => apiFetch(`/me/invoices/${invoiceId}/price-breakdown`),
    enabled: !!invoiceId,
  });
}

export function useSubmitBankTransferReceipt() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ submission, idempotencyKey }) =>
      apiFetch(submission.path, {
        method: 'POST',
        body: submission.body,
        headers: { 'Idempotency-Key': idempotencyKey },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me'] });
    },
    onError: (error) => {
      if (error?.status === 409) queryClient.invalidateQueries({ queryKey: ['me'] });
    },
  });
}

export function useMyOpenInvoices() {
  const { data: standings, isLoading: loadingStandings } = useEnrollmentPaymentStatus();
  const results = useQueries({
    queries: (standings ?? []).map((enrollment) => {
      const segment = enrollment.enrollmentType === 'CourseEnrollment' ? 'course-enrollments' : 'program-enrollments';
      return {
        queryKey: ['me', segment, enrollment.enrollmentId, 'invoices'],
        queryFn: () => apiFetch(`/me/${segment}/${enrollment.enrollmentId}/invoices`),
      };
    }),
  });
  const loading = loadingStandings || results.some((result) => result.isLoading);
  const invoices = results.flatMap((result) => result.data ?? []);
  return { invoices, isLoading: loading };
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

export function usePreviewCashPayment(studentId) {
  return useMutation({
    mutationFn: (amount) =>
      apiFetch(`/students/${studentId}/cash-payments/preview`, { method: 'POST', body: JSON.stringify({ amount }) }),
  });
}

export function useRegisterCashPayment(studentId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ key, payload }) =>
      apiFetch(`/students/${studentId}/cash-payments`, {
        method: 'POST',
        headers: { 'Idempotency-Key': key },
        body: JSON.stringify(payload),
      }),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['students'] });
    },
  });
}

export function useComprobantesAdmin() {
  return useQuery({
    queryKey: ['admin', 'bank-transfer-receipts'],
    queryFn: () => apiFetch('/bank-transfer-receipts'),
  });
}

export function useAprobarComprobante() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/bank-transfer-receipts/${id}/approve`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'bank-transfer-receipts'] });
      queryClient.invalidateQueries({ queryKey: ['students'] });
    },
  });
}

export function useRechazarComprobante() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, rejectionReason }) =>
      apiFetch(`/bank-transfer-receipts/${id}/reject`, { method: 'POST', body: JSON.stringify({ rejectionReason }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'bank-transfer-receipts'] }),
  });
}
