import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const CURRENCY_REQUIRED = 'CURRENCY_REQUIRED';

export function useMonthlyMetric(panel, { from, to, currency, currencyPending = false, invoiceType, programId, courseId }, basePath = '/billing/metrics') {
  const params = new URLSearchParams({ from, to });
  if (currency) params.set('currency', currency);
  if (invoiceType) params.set('invoiceType', invoiceType);
  if (programId) params.set('programId', programId);
  if (courseId) params.set('courseId', courseId);
  const query = useQuery({
    queryKey: ['billing-metrics', basePath, panel, from, to, currency ?? null, invoiceType ?? null, programId ?? null, courseId ?? null],
    queryFn: () => apiFetch(`${basePath}/${panel}?${params.toString()}`),
    enabled: Boolean(from && to),
    placeholderData: (previous) => previous,
  });
  const awaitsCurrency = currencyPending && query.error?.code === CURRENCY_REQUIRED;
  return {
    ...query,
    isLoading: query.isLoading || awaitsCurrency,
    isError: query.isError && !awaitsCurrency,
  };
}
