import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useNightlyRuns() {
  return useQuery({ queryKey: ['billing-runs', 'list'], queryFn: () => apiFetch('/billing-runs') });
}

export function useNightlyRunStepItems(nightlyRunId, step, { enabled = true } = {}) {
  return useQuery({
    queryKey: ['billing-runs', 'items', nightlyRunId, step],
    queryFn: () => apiFetch(`/billing-runs/${nightlyRunId}/steps/${step}/items`),
    enabled: enabled && !!nightlyRunId && !!step,
  });
}
