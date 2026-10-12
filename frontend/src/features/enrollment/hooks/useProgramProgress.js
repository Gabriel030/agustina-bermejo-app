import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useProgresoStaff(programEnrollmentId) {
  return useQuery({
    queryKey: ['program-enrollments', programEnrollmentId, 'progress'],
    queryFn: () => apiFetch(`/program-enrollments/${programEnrollmentId}/progress`),
    enabled: !!programEnrollmentId,
  });
}
