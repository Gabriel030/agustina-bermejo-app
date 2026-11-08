import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const MODULES_STALE_TIME_MS = 60_000;

export const myModulesQuery = {
  queryKey: ['me', 'modules'],
  queryFn: () => apiFetch('/me/modules'),
  staleTime: MODULES_STALE_TIME_MS,
};

export function useMyModules({ enabled = true } = {}) {
  const query = useQuery({ ...myModulesQuery, enabled });
  const billingMode = query.data?.billing?.mode;
  return {
    isLoading: enabled && query.isLoading,
    isError: enabled && query.isError,
    error: query.error,
    refetch: query.refetch,
    billingMode,
    billingEnabled: Boolean(billingMode) && billingMode !== 'OFF',
  };
}
