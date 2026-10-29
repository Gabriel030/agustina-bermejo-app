import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useCatalogPrograms() {
  return useQuery({
    queryKey: ['catalog', 'programs'],
    queryFn: () => apiFetch('/programs?status=ACTIVE&visibleInCatalog=true'),
  });
}

export function useCatalogCourses() {
  return useQuery({
    queryKey: ['catalog', 'courses'],
    queryFn: () => apiFetch('/courses?status=ACTIVE&visibleInCatalog=true'),
  });
}

export function usePrograma(id) {
  return useQuery({ queryKey: ['programa', id], queryFn: () => apiFetch(`/programs/${id}`), enabled: !!id });
}

export function useCurso(id) {
  return useQuery({ queryKey: ['curso', id], queryFn: () => apiFetch(`/courses/${id}`), enabled: !!id });
}

export function useProgramaPricing(id) {
  return useQuery({ queryKey: ['programa', id, 'pricing'], queryFn: () => apiFetch(`/programs/${id}/pricing`), enabled: !!id });
}

export function useCursoPricing(id) {
  return useQuery({ queryKey: ['curso', id, 'pricing'], queryFn: () => apiFetch(`/courses/${id}/pricing`), enabled: !!id });
}

export function useOpenProgramCohorts(id) {
  return useQuery({
    queryKey: ['programa', id, 'cohorts'],
    queryFn: () => apiFetch(`/programs/${id}/cohorts`),
    enabled: !!id,
    select: (cohorts) => cohorts.filter((cohort) => cohort.admissionOpen),
  });
}

export function useProgramaPlanDeEstudio(id) {
  return useQuery({ queryKey: ['programa', id, 'subjects'], queryFn: () => apiFetch(`/programs/${id}/subjects`), enabled: !!id });
}

export function useCursoTemario(id) {
  return useQuery({ queryKey: ['curso', id, 'subjects'], queryFn: () => apiFetch(`/courses/${id}/subjects`), enabled: !!id });
}

export function useMisSolicitudesCarrera() {
  return useQuery({ queryKey: ['mis-solicitudes-carrera'], queryFn: () => apiFetch('/me/program-enrollment-applications') });
}

export function useMisSolicitudesCurso() {
  return useQuery({ queryKey: ['mis-solicitudes-curso'], queryFn: () => apiFetch('/me/course-enrollment-applications') });
}

export function useSolicitarCarrera() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ programId, cohortId }) => {
      const formData = new FormData();
      formData.append('programId', programId);
      formData.append('cohortId', cohortId);
      return apiFetch('/me/program-enrollment-applications', { method: 'POST', body: formData });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['mis-solicitudes-carrera'] }),
  });
}

export function useInscribirmeCurso() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ body, idempotencyKey }) =>
      apiFetch('/me/course-enrollments', { method: 'POST', body, headers: { 'Idempotency-Key': idempotencyKey } }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mis-solicitudes-curso'] });
      queryClient.invalidateQueries({ queryKey: ['me'] });
    },
  });
}

export function useActualizarPerfilPropio() {
  return useMutation({
    mutationFn: (datos) => apiFetch('/me', { method: 'PUT', body: JSON.stringify(datos) }),
  });
}

export function useAgregarDocumentoSolicitudCarrera() {
  return useMutation({
    mutationFn: ({ applicationId, type, file }) => {
      const formData = new FormData();
      formData.append('file', file);
      return apiFetch(`/me/program-enrollment-applications/${applicationId}/documents?type=${type}`, {
        method: 'POST',
        body: formData,
      });
    },
  });
}
