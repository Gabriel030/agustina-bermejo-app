import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { STALE_TIME } from '@/api/cachePolicy';

export function useMisInscripciones(enabled = true) {
  return useQuery({ queryKey: ['me', 'section-enrollments'], queryFn: () => apiFetch('/me/section-enrollments'), staleTime: STALE_TIME.OPERATIONAL, enabled });
}

export function useSeccionesDeMateria(subjectId) {
  return useQuery({
    queryKey: ['subjects', subjectId, 'sections'],
    queryFn: () => apiFetch(`/subjects/${subjectId}/sections?status=ACTIVE`),
    enabled: !!subjectId,
  });
}

export function useInscribirmeAMateria() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ programEnrollmentId, sectionId }) =>
      apiFetch('/me/section-enrollments', { method: 'POST', body: JSON.stringify({ programEnrollmentId, sectionId }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'section-enrollments'] }),
  });
}

export function useEstadoMatricula(programEnrollmentId) {
  return useQuery({
    queryKey: ['me', 'program-enrollments', programEnrollmentId, 'enrollment-fee-status'],
    queryFn: () => apiFetch(`/me/program-enrollments/${programEnrollmentId}/enrollment-fee-status`),
    enabled: !!programEnrollmentId,
  });
}

export function useMisCursos(enabled = true) {
  return useQuery({ queryKey: ['me', 'course-enrollments'], queryFn: () => apiFetch('/me/course-enrollments'), enabled });
}

export function useMyCourseEnrollment(id, enabled = true) {
  return useQuery({
    queryKey: ['me', 'course-enrollments', id],
    queryFn: () => apiFetch(`/me/course-enrollments/${id}`),
    enabled: !!id && enabled,
  });
}

export function useMisMatriculaciones(enabled = true) {
  return useQuery({ queryKey: ['me', 'program-enrollments'], queryFn: () => apiFetch('/me/program-enrollments'), enabled });
}

export function useMiProgreso(programEnrollmentId) {
  return useQuery({
    queryKey: ['me', 'program-enrollments', programEnrollmentId, 'progress'],
    queryFn: () => apiFetch(`/me/program-enrollments/${programEnrollmentId}/progress`),
    enabled: !!programEnrollmentId,
  });
}

export function useMiInscripcion(sectionEnrollmentId, enabled = true) {
  const { data: inscripciones, ...resto } = useMisInscripciones(enabled);
  const inscripcion = inscripciones?.find((i) => String(i.id) === String(sectionEnrollmentId));
  return { ...resto, data: inscripcion };
}

export function useDarmeDeBajaDeMateria() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (sectionEnrollmentId) => apiFetch(`/me/section-enrollments/${sectionEnrollmentId}/withdraw`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'section-enrollments'] }),
  });
}

export function useChangeMySection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ sectionEnrollmentId, newSectionId }) =>
      apiFetch(`/me/section-enrollments/${sectionEnrollmentId}/change-section`, { method: 'POST', body: JSON.stringify({ newSectionId }) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me', 'section-enrollments'] });
      queryClient.invalidateQueries({ queryKey: ['me', 'program-enrollments'] });
      queryClient.invalidateQueries({ queryKey: ['me', 'procedures'] });
    },
  });
}
