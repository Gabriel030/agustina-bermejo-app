import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function usePreviewFeatureChange({ code, targetMode, enabled }) {
  return useQuery({
    queryKey: ['platform', 'feature-preview', code, targetMode],
    queryFn: () => apiFetch(`/platform/features/${code}/preview?target=${targetMode}`),
    enabled,
    staleTime: 0,
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
  });
}
