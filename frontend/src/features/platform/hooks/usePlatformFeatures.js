import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export const PLATFORM_FEATURES_KEY = ['platform', 'features'];

export function usePlatformFeatures() {
  return useQuery({
    queryKey: PLATFORM_FEATURES_KEY,
    queryFn: () => apiFetch('/platform/features'),
  });
}
