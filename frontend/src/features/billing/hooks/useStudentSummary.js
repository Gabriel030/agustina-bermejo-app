import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useStudentSummary(studentId) {
  return useQuery({
    queryKey: ['alumno', studentId],
    queryFn: () => apiFetch(`/students/${studentId}`),
    enabled: !!studentId,
  });
}
