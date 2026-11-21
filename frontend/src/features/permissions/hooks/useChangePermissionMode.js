import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { PERMISSION_DISCREPANCIES_KEY, PERMISSION_DISCREPANCY_SUMMARY_KEY, PERMISSION_GRID_KEY } from '@/features/permissions/hooks/usePermissionGrid';

export function useChangePermissionMode() {
  const queryClient = useQueryClient();
  return useMutation({
    meta: { handlesOwnError: true },
    mutationFn: ({ reason, role, module, mode }) =>
      apiFetch('/permissions/modes', { method: 'PUT', body: JSON.stringify({ reason, changes: [{ role, module, mode }] }) }),
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: PERMISSION_GRID_KEY }),
        queryClient.invalidateQueries({ queryKey: PERMISSION_DISCREPANCY_SUMMARY_KEY }),
        queryClient.invalidateQueries({ queryKey: PERMISSION_DISCREPANCIES_KEY }),
      ]),
  });
}
