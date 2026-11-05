import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export const FEATURE_CHANGES_KEY = ['platform', 'feature-changes'];
export const FEATURE_CHANGES_PAGE_SIZE = 25;

export function useFeatureChanges({ code, page }) {
  return useQuery({
    queryKey: [...FEATURE_CHANGES_KEY, code ?? 'ALL', page],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page - 1), size: String(FEATURE_CHANGES_PAGE_SIZE) });
      if (code) params.set('code', code);
      return apiFetch(`/platform/features/changes?${params}`);
    },
    placeholderData: keepPreviousData,
  });
}
