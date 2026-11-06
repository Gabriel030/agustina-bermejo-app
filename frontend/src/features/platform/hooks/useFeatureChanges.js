import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export const FEATURE_CHANGES_KEY = ['platform', 'feature-changes'];
export const FEATURE_CHANGES_PAGE_SIZE = 25;

export function useFeatureChanges({ code, actorKind, from, to, page }) {
  return useQuery({
    queryKey: [...FEATURE_CHANGES_KEY, code ?? 'ALL', actorKind ?? 'ALL', from ?? '', to ?? '', page],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page - 1), size: String(FEATURE_CHANGES_PAGE_SIZE) });
      if (code) params.set('code', code);
      if (actorKind) params.set('actorKind', actorKind);
      if (from) params.set('from', from);
      if (to) params.set('to', to);
      return apiFetch(`/platform/features/changes?${params}`);
    },
    placeholderData: keepPreviousData,
  });
}
