import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useInscripcionesACarrera(studentId) {
  return useQuery({
    queryKey: ['alumno', studentId, 'program-enrollments'],
    queryFn: () => apiFetch(`/students/${studentId}/program-enrollments`),
    enabled: !!studentId,
  });
}

export function useInscripcionesACurso(studentId) {
  return useQuery({
    queryKey: ['alumno', studentId, 'course-enrollments'],
    queryFn: () => apiFetch(`/students/${studentId}/course-enrollments`),
    enabled: !!studentId,
  });
}

export function useEstadoMatriculaDeInscripcion(programEnrollmentId) {
  return useQuery({
    queryKey: ['program-enrollment', programEnrollmentId, 'enrollment-fee-status'],
    queryFn: () => apiFetch(`/program-enrollments/${programEnrollmentId}/enrollment-fee-status`),
    enabled: !!programEnrollmentId,
  });
}

export function useHabilitacionesDeMatricula(programEnrollmentId) {
  return useQuery({
    queryKey: ['program-enrollment', programEnrollmentId, 'enrollment-fee-exemptions'],
    queryFn: () => apiFetch(`/program-enrollments/${programEnrollmentId}/enrollment-fee-exemptions`),
    enabled: !!programEnrollmentId,
  });
}

export function useOtorgarHabilitacion(programEnrollmentId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) =>
      apiFetch(`/program-enrollments/${programEnrollmentId}/enrollment-fee-exemptions`, { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['program-enrollment', programEnrollmentId] }),
  });
}

export function useRevocarHabilitacion(programEnrollmentId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ exemptionId, notes }) =>
      apiFetch(`/enrollment-fee-exemptions/${exemptionId}/revoke`, { method: 'POST', body: JSON.stringify({ notes }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['program-enrollment', programEnrollmentId] }),
  });
}
