import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useCiclos({ programId, courseId, status } = {}) {
  const params = new URLSearchParams();
  if (programId) params.set('programId', programId);
  if (courseId) params.set('courseId', courseId);
  if (status) params.set('status', status);
  const qs = params.toString();

  return useQuery({
    queryKey: ['ciclos', { programId, courseId, status }],
    queryFn: () => apiFetch(`/academic-offering-cycles${qs ? `?${qs}` : ''}`),
  });
}

export function useCicloSecciones(cycleId) {
  return useQuery({
    queryKey: ['ciclo-secciones', cycleId],
    queryFn: () => apiFetch(`/academic-offering-cycles/${cycleId}/sections`),
    enabled: !!cycleId,
  });
}

export function useCreateCiclo() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) =>
      apiFetch('/academic-offering-cycles', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ciclos'] }),
  });
}

export function useAddSeccion(cycleId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) =>
      apiFetch(`/academic-offering-cycles/${cycleId}/sections`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ciclo-secciones', cycleId] }),
  });
}

export function useAsignarInstructorSeccion(tipoCiclo) {
  return useMutation({
    mutationFn: ({ sectionId, instructorId }) => {
      const path = tipoCiclo === 'programa' ? 'program-sections' : 'course-sections';
      return apiFetch(`/${path}/${sectionId}/instructors`, {
        method: 'POST',
        body: JSON.stringify({ instructorId, role: 'LEAD' }),
      });
    },
  });
}

export function useSubmitCiclo() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (cycleId) =>
      apiFetch(`/academic-offering-cycles/${cycleId}/submit`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ciclos'] }),
  });
}
