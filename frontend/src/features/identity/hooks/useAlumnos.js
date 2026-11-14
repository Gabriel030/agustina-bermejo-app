import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export const ALUMNOS_PAGE_SIZE = 25;

export function useAlumnos({ status, onboardingStatus, search, page = 0, size = ALUMNOS_PAGE_SIZE, enabled = true } = {}) {
  return useQuery({
    queryKey: ['alumnos', { status, onboardingStatus, search, page, size }],
    queryFn: () => {
      const params = new URLSearchParams();
      if (status) params.set('status', status);
      if (onboardingStatus) params.set('onboardingStatus', onboardingStatus);
      if (search) params.set('search', search);
      params.set('page', String(page));
      params.set('size', String(size));
      return apiFetch(`/students?${params.toString()}`);
    },
    staleTime: 30_000,
    placeholderData: (previous) => previous,
    enabled,
  });
}
