import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useStudentRegularity(kind, enrollmentId, enabled = true) {
  return useQuery({
    queryKey: ['regular-certificates', 'eligibility', kind, enrollmentId],
    queryFn: () => apiFetch(`/regular-certificates/eligibility?kind=${kind}&enrollmentId=${enrollmentId}`),
    enabled: enabled && !!enrollmentId,
  });
}
