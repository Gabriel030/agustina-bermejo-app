import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { useInscripcionesACarrera, useInscripcionesACurso } from '@/features/identity/hooks/useMatriculaAlumno';
import { buildEnrollmentOptions } from '@/features/identity/lib/academicRecord';

export function useProgramProgress(programEnrollmentId) {
  return useQuery({
    queryKey: ['program-enrollment', programEnrollmentId, 'progress'],
    queryFn: () => apiFetch(`/program-enrollments/${programEnrollmentId}/progress`),
    enabled: !!programEnrollmentId,
  });
}

export function useProgramSectionEnrollments(programEnrollmentId) {
  return useQuery({
    queryKey: ['program-enrollment', programEnrollmentId, 'section-enrollments'],
    queryFn: () => apiFetch(`/program-enrollments/${programEnrollmentId}/section-enrollments`),
    enabled: !!programEnrollmentId,
  });
}

export function useProgramAttendanceSummary(programEnrollmentId) {
  return useQuery({
    queryKey: ['program-enrollment', programEnrollmentId, 'attendance-summary'],
    queryFn: () => apiFetch(`/program-enrollments/${programEnrollmentId}/attendance-summary`),
    enabled: !!programEnrollmentId,
  });
}

export function useCourseProgress(courseEnrollmentId) {
  return useQuery({
    queryKey: ['course-enrollment', courseEnrollmentId, 'progress'],
    queryFn: () => apiFetch(`/course-enrollments/${courseEnrollmentId}/progress`),
    enabled: !!courseEnrollmentId,
  });
}

export function useCourseSectionEnrollments(courseEnrollmentId) {
  return useQuery({
    queryKey: ['course-enrollment', courseEnrollmentId, 'section-enrollments'],
    queryFn: () => apiFetch(`/course-enrollments/${courseEnrollmentId}/section-enrollments`),
    enabled: !!courseEnrollmentId,
  });
}

export function useSectionGradeItems(kind, sectionEnrollmentId) {
  const path = kind === 'course' ? 'course-section-enrollments' : 'section-enrollments';
  return useQuery({
    queryKey: [path, sectionEnrollmentId, 'grade-items'],
    queryFn: () => apiFetch(`/${path}/${sectionEnrollmentId}/grade-items`),
    enabled: !!sectionEnrollmentId,
  });
}

export function useEnrollmentOptions(studentId) {
  const programs = useInscripcionesACarrera(studentId);
  const courses = useInscripcionesACurso(studentId);
  return {
    options: buildEnrollmentOptions(programs.data, courses.data),
    isLoading: programs.isLoading || courses.isLoading,
    isError: programs.isError || courses.isError,
    refetch: () => Promise.all([programs.refetch(), courses.refetch()]),
  };
}
