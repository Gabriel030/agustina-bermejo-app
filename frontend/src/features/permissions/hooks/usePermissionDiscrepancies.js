import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { PERMISSION_DISCREPANCIES_KEY, PERMISSION_DISCREPANCY_SUMMARY_KEY } from '@/features/permissions/hooks/usePermissionGrid';
import { DISCREPANCY_PAGE_SIZE } from '@/features/permissions/lib/permissionGrid';

export function usePermissionDiscrepancySummary({ enabled }) {
  return useQuery({
    queryKey: PERMISSION_DISCREPANCY_SUMMARY_KEY,
    queryFn: () => apiFetch('/permissions/discrepancies/summary'),
    enabled,
  });
}

export function usePermissionDiscrepancies({ module, page }) {
  return useQuery({
    queryKey: [...PERMISSION_DISCREPANCIES_KEY, { module, page }],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: String(DISCREPANCY_PAGE_SIZE) });
      if (module) params.set('module', module);
      return apiFetch(`/permissions/discrepancies?${params}`);
    },
    placeholderData: (previous) => previous,
  });
}
