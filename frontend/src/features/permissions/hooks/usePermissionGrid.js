import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export const PERMISSION_GRID_KEY = ['permissions', 'grid'];
export const PERMISSION_DISCREPANCIES_KEY = ['permissions', 'discrepancies'];
export const PERMISSION_DISCREPANCY_SUMMARY_KEY = ['permissions', 'discrepancy-summary'];
export const PERMISSION_CATALOG_KEY = ['permissions', 'catalog'];

const retryUnlessForbidden = (count, error) => error?.status !== 403 && count < 2;

export function usePermissionCatalog() {
  return useQuery({ queryKey: PERMISSION_CATALOG_KEY, queryFn: () => apiFetch('/permissions/catalog'), retry: retryUnlessForbidden });
}

export function usePermissionGridData() {
  return useQuery({ queryKey: PERMISSION_GRID_KEY, queryFn: () => apiFetch('/permissions/grid'), retry: retryUnlessForbidden });
}
