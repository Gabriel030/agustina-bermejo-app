import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { STALE_TIME } from '@/api/cachePolicy';

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
  });
}

export function useStaffCurriculumProgress(programEnrollmentId) {
  return useQuery({
    queryKey: ['program-enrollments', programEnrollmentId, 'progress', 'subjects'],
    queryFn: () => apiFetch(`/program-enrollments/${programEnrollmentId}/progress/subjects`),
    staleTime: STALE_TIME.OPERATIONAL,
    enabled: !!programEnrollmentId,
  });
}

export function useStaffGraduationProjection(programEnrollmentId) {
  return useQuery({
    queryKey: ['program-enrollments', programEnrollmentId, 'progress', 'projection'],
    queryFn: () => apiFetch(`/program-enrollments/${programEnrollmentId}/progress/projection`),
    staleTime: STALE_TIME.OPERATIONAL,
    enabled: !!programEnrollmentId,
  });
}

export function useCurriculumProgressFor(audience, programEnrollmentId) {
  const student = useCurriculumProgress(audience === 'staff' ? null : programEnrollmentId);
  const staff = useStaffCurriculumProgress(audience === 'staff' ? programEnrollmentId : null);
  return audience === 'staff' ? staff : student;
}

export function useGraduationProjectionFor(audience, programEnrollmentId) {
  const student = useGraduationProjection(audience === 'staff' ? null : programEnrollmentId);
  const staff = useStaffGraduationProjection(audience === 'staff' ? programEnrollmentId : null);
  return audience === 'staff' ? staff : student;
}
