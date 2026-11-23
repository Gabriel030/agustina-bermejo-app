import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useChangeSectionCandidates(sectionEnrollmentId, enabled, { own = false } = {}) {
  const prefix = own ? '/me' : '';
  return useQuery({
    queryKey: [...(own ? ['me'] : []), 'section-enrollments', sectionEnrollmentId, 'change-section-candidates'],
    queryFn: () => apiFetch(`${prefix}/section-enrollments/${sectionEnrollmentId}/change-section-candidates`),
    enabled: !!sectionEnrollmentId && enabled,
    staleTime: 0,
  });
}
