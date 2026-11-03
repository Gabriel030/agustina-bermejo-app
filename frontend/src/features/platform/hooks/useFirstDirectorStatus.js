import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export const FIRST_DIRECTOR_STATUS_KEY = ['platform', 'first-director-status'];

export function useFirstDirectorStatus() {
  return useQuery({
    queryKey: FIRST_DIRECTOR_STATUS_KEY,
    queryFn: () => apiFetch('/platform/provisioning/first-director/status'),
  });
}
