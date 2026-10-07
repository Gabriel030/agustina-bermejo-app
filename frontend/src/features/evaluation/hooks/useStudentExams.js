import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useMesasDisponibles(subjectId) {
  return useQuery({
    queryKey: ['me', 'exam-boards', subjectId],
    queryFn: () => apiFetch(`/me/exam-boards?subjectId=${subjectId}`),
    enabled: !!subjectId,
  });
}

export function useMisInscripcionesAMesas() {
  return useQuery({ queryKey: ['me', 'exam-board-registrations'], queryFn: () => apiFetch('/me/exam-board-registrations') });
}

export function useInscribirmeAMesa() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ examBoardId, programSectionEnrollmentId }) =>
      apiFetch('/me/exam-board-registrations', { method: 'POST', body: JSON.stringify({ examBoardId, programSectionEnrollmentId }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'exam-board-registrations'] }),
  });
}

export function useDarmeDeBajaDeMesa() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (registrationId) => apiFetch(`/me/exam-board-registrations/${registrationId}/withdraw`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'exam-board-registrations'] }),
  });
}
