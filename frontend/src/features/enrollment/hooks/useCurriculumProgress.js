import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { STALE_TIME } from '@/api/cachePolicy';

const NON_RETRYABLE_STATUSES = [403, 404];

function retryUnlessDenied(failureCount, error) {
  return !NON_RETRYABLE_STATUSES.includes(error?.status) && failureCount < 2;
}

export function useCurriculumProgress(programEnrollmentId) {
  return useQuery({
    queryKey: ['me', 'program-enrollments', programEnrollmentId, 'progress', 'subjects'],
    queryFn: () => apiFetch(`/me/program-enrollments/${programEnrollmentId}/progress/subjects`),
    staleTime: STALE_TIME.OPERATIONAL,
    enabled: !!programEnrollmentId,
  });
}

export function useGraduationProjection(programEnrollmentId) {
  return useQuery({
    queryKey: ['me', 'program-enrollments', programEnrollmentId, 'progress', 'projection'],
    queryFn: () => apiFetch(`/me/program-enrollments/${programEnrollmentId}/progress/projection`),
    staleTime: STALE_TIME.OPERATIONAL,
    enabled: !!programEnrollmentId,
    retry: retryUnlessDenied,
  });
}
