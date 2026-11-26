import { useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const CURRENCY_REQUIRED = 'CURRENCY_REQUIRED';

function metricKey(basePath, panel, { from, to, currency, invoiceType, programId, courseId }) {
  return ['billing-metrics', basePath, panel, from, to, currency ?? null, invoiceType ?? null, programId ?? null, courseId ?? null];
}

export function useMonthlyMetric(panel, filters, basePath = '/billing/metrics') {
  const { from, to, currencyPending = false, invoiceType, programId, courseId } = filters;
  const queryClient = useQueryClient();
  const resolvedByServer = filters.currency && queryClient.getQueryData(metricKey(basePath, panel, { ...filters, currency: undefined }))?.currency === filters.currency;
  const currency = resolvedByServer ? undefined : filters.currency;
  const params = new URLSearchParams({ from, to });
  if (currency) params.set('currency', currency);
  if (invoiceType) params.set('invoiceType', invoiceType);
  if (programId) params.set('programId', programId);
  if (courseId) params.set('courseId', courseId);
  const query = useQuery({
    queryKey: metricKey(basePath, panel, { from, to, currency, invoiceType, programId, courseId }),
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
