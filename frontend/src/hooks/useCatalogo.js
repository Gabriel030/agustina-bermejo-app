import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

// Primer uso de React Query del proyecto (ver docs/context/frontend.md) — un
// hook por recurso, ningún componente de pantalla hace fetch directo.

export function useProgramas() {
  return useQuery({ queryKey: ['programas'], queryFn: () => apiFetch('/programs?status=ACTIVE') });
}

export function useCursos() {
  return useQuery({ queryKey: ['cursos'], queryFn: () => apiFetch('/courses?status=ACTIVE') });
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

export function useProgramaCohorts(id) {
  return useQuery({ queryKey: ['programa', id, 'cohorts'], queryFn: () => apiFetch(`/programs/${id}/cohorts`), enabled: !!id });
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

// Subir el comprobante invalida "mis solicitudes" (de carrera o de curso, según
// corresponda) para que la tab "Mis solicitudes" refleje el nuevo estado sin
// necesitar un reload manual — el caso de uso concreto que justificó sumar
// React Query en esta fase (ver docs/context/frontend.md).
export function useSolicitarCarrera() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ programId, cohortId, comprobante }) => {
      const formData = new FormData();
      formData.append('programId', programId);
      formData.append('cohortId', cohortId);
      formData.append('comprobante', comprobante);
      return apiFetch('/me/program-enrollment-applications', { method: 'POST', body: formData });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['mis-solicitudes-carrera'] }),
  });
}

export function useInscribirmeCurso() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ courseId, comprobante }) => {
      const formData = new FormData();
      formData.append('courseId', courseId);
      formData.append('comprobante', comprobante);
      return apiFetch('/me/course-enrollments', { method: 'POST', body: formData });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['mis-solicitudes-curso'] }),
  });
}
