import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function usePaymentStanding() {
  return useQuery({
    queryKey: ['me', 'billing-standing'],
    queryFn: () => apiFetch('/me/billing-standing'),
    select: (entries) => ({
      standing: entries.some((e) => e.billingStanding !== 'UP_TO_DATE') ? 'MOROSO' : 'AL_DIA',
    }),
  });
}

export function useEstadoDePago(studentId) {
  return useQuery({
    queryKey: ['students', studentId, 'billing-overview'],
    queryFn: () => apiFetch(`/students/${studentId}/billing-overview`),
    enabled: !!studentId,
    select: (entries) => ({
      standing: entries.some((e) => e.billingStanding !== 'UP_TO_DATE') ? 'MOROSO' : 'AL_DIA',
    }),
  });
}
