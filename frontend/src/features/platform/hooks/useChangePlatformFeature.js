import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { PLATFORM_FEATURES_KEY } from '@/features/platform/hooks/usePlatformFeatures';
import { FEATURE_CHANGES_KEY } from '@/features/platform/hooks/useFeatureChanges';

export function useChangePlatformFeature() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ code, version, request }) =>
      apiFetch(`/platform/features/${code}`, {
        method: 'PUT',
        headers: { 'If-Match': String(version) },
        body: JSON.stringify(request),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PLATFORM_FEATURES_KEY });
      queryClient.invalidateQueries({ queryKey: FEATURE_CHANGES_KEY });
      queryClient.invalidateQueries({ queryKey: ['me', 'modules'] });
    },
  });
}
