import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const MODULES_STALE_TIME_MS = 60_000;

export function useMyModules({ enabled = true } = {}) {
  const query = useQuery({
    queryKey: ['me', 'modules'],
    queryFn: () => apiFetch('/me/modules'),
    staleTime: MODULES_STALE_TIME_MS,
    enabled,
  });
  const billingMode = query.data?.billing?.mode;
  return {
    isLoading: enabled && query.isLoading,
    isError: enabled && query.isError,
    error: query.error,
    refetch: query.refetch,
    billingEnabled: Boolean(billingMode) && billingMode !== 'OFF',
  };
}
