import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

function post(path, body) {
  return apiFetch(path, { method: 'POST', body: JSON.stringify(body) });
}

const WITHDRAW_PATHS = {
  'withdraw-subject': (id) => `/section-enrollments/${id}/withdraw`,
  'withdraw-module': (id) => `/course-section-enrollments/${id}/withdraw`,
  'withdraw-program': (id) => `/program-enrollments/${id}/withdraw`,
  'withdraw-course': (id) => `/course-enrollments/${id}/withdraw`,
};

export function useWithdrawEnrollment(studentId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ kind, id, reason, effectiveDate }) =>
      post(WITHDRAW_PATHS[kind](id), effectiveDate ? { reason, effectiveDate } : { reason }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['program-enrollment'] });
      queryClient.invalidateQueries({ queryKey: ['course-enrollment'] });
      queryClient.invalidateQueries({ queryKey: ['alumno', studentId] });
    },
  });
}

export function useChangeSection(studentId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ sectionEnrollmentId, newSectionId, reason }) =>
      post(`/section-enrollments/${sectionEnrollmentId}/change-section`, { newSectionId, reason }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['program-enrollment'] });
      queryClient.invalidateQueries({ queryKey: ['alumno', studentId] });
    },
  });
}

export function useChangeSectionCandidates(sectionEnrollmentId, enabled) {
  return useQuery({
    queryKey: ['section-enrollments', sectionEnrollmentId, 'change-section-candidates'],
    queryFn: () => apiFetch(`/section-enrollments/${sectionEnrollmentId}/change-section-candidates`),
    enabled: !!sectionEnrollmentId && enabled,
    staleTime: 0,
  });
}
