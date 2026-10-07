import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function usePaymentStanding() {
  return useQuery({
    queryKey: ['me', 'enrollment-payment-status'],
    queryFn: () => apiFetch('/me/enrollment-payment-status'),
    select: (entries) => ({
      standing: entries.some((e) => e.paymentStatus !== 'UP_TO_DATE') ? 'MOROSO' : 'AL_DIA',
    }),
  });
}

export function useEstadoDePago(studentId) {
  return useQuery({
    queryKey: ['students', studentId, 'billing-overview'],
    queryFn: () => apiFetch(`/students/${studentId}/billing-overview`),
    enabled: !!studentId,
    select: (entries) => ({
      standing: entries.some((e) => e.paymentStatus !== 'UP_TO_DATE') ? 'MOROSO' : 'AL_DIA',
    }),
  });
}
