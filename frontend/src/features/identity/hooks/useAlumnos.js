import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useAlumnos({ status, onboardingStatus, search } = {}) {
  return useQuery({
    queryKey: ['alumnos', { status, onboardingStatus, search }],
    queryFn: () => {
      const params = new URLSearchParams();
      if (status) params.set('status', status);
      if (onboardingStatus) params.set('onboardingStatus', onboardingStatus);
      if (search) params.set('search', search);
      const qs = params.toString();
      return apiFetch(`/students${qs ? `?${qs}` : ''}`);
    },
    staleTime: 30_000,
  });
}
