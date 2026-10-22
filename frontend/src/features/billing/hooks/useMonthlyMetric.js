import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useMonthlyMetric(panel, { from, to, currency, invoiceType, programId, courseId }) {
  const params = new URLSearchParams({ from, to });
  if (currency) params.set('currency', currency);
  if (invoiceType) params.set('invoiceType', invoiceType);
  if (programId) params.set('programId', programId);
  if (courseId) params.set('courseId', courseId);
  return useQuery({
    queryKey: ['billing-metrics', panel, from, to, currency ?? null, invoiceType ?? null, programId ?? null, courseId ?? null],
    queryFn: () => apiFetch(`/billing/metrics/${panel}?${params.toString()}`),
    enabled: Boolean(from && to),
  });
}
