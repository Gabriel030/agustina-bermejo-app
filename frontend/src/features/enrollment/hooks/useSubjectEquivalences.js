import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { STALE_TIME } from '@/api/cachePolicy';

export const EQUIVALENCE_STATUSES = ['ACTIVE', 'VOIDED', 'ALL'];

export function useSubjectEquivalences(programEnrollmentId, status = 'ALL') {
  return useQuery({
    queryKey: ['program-enrollments', programEnrollmentId, 'equivalences', status],
    queryFn: () => apiFetch(`/program-enrollments/${programEnrollmentId}/equivalences?status=${status}`),
    staleTime: STALE_TIME.OPERATIONAL,
    enabled: !!programEnrollmentId,
  });
}
