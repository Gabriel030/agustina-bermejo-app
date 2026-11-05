import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiFetchWithTotal } from '@/api/client';

const RETRYABLE_RESULTS = ['FAILED', 'RETRIES_EXHAUSTED'];
const RECOVERED_RESULTS = ['ISSUED', 'ALREADY_ISSUED'];
const RUNNING_REFETCH_MS = 5000;

export function useMonthlyRuns({ period, size = 50 } = {}) {
  const query = new URLSearchParams({ size: String(size) });
  if (period) query.set('period', period);
  return useQuery({
    queryKey: ['monthly-runs', 'list', period ?? null, size],
    queryFn: () => apiFetchWithTotal(`/monthly-invoice-runs?${query}`),
    refetchInterval: (current) => (current.state.data?.items?.some((candidate) => candidate.status === 'RUNNING') ? RUNNING_REFETCH_MS : false),
  });
}

export function useMonthlyRunItems(monthlyRunId, { size = 100 } = {}) {
  return useQuery({
    queryKey: ['monthly-runs', 'items', monthlyRunId, size],
    queryFn: () => apiFetchWithTotal(`/monthly-invoice-runs/${monthlyRunId}/items?size=${size}`),
    enabled: !!monthlyRunId,
    placeholderData: keepPreviousData,
  });
}

function retryRequest({ monthlyRunId, itemId }) {
  return apiFetch(`/monthly-invoice-runs/${monthlyRunId}/items/${itemId}/retries`, { method: 'POST' });
}

export function useRetryMonthlyRunItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: retryRequest,
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['monthly-runs'] }),
  });
}

export function useRetryFailedMonthlyRunItems() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ monthlyRunId, items }) => {
      const outcome = { recovered: 0, stillFailing: 0, errored: 0 };
      for (const item of items.filter((candidate) => RETRYABLE_RESULTS.includes(candidate.result))) {
        try {
          const retried = await retryRequest({ monthlyRunId, itemId: item.id });
          if (RECOVERED_RESULTS.includes(retried?.result)) outcome.recovered += 1;
          else outcome.stillFailing += 1;
        } catch {
          outcome.errored += 1;
        }
      }
      return outcome;
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['monthly-runs'] }),
  });
}

export function useSimulateMonthlyRun() {
  return useMutation({
    mutationFn: ({ period }) => apiFetch('/monthly-invoice-runs/simulations', { method: 'POST', body: JSON.stringify({ period }) }),
  });
}

export function useIssueMonthlyRunManually() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ period, simulationRunId }) =>
      apiFetch('/monthly-invoice-runs/manual-issuances', { method: 'POST', body: JSON.stringify({ period, simulationRunId }) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['monthly-runs'] }),
  });
}
