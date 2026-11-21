import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useRequestSectionModification(cycleId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ sectionId, slots }) =>
      apiFetch(`/subject-sections/${sectionId}/modification-request`, {
        method: 'POST',
        body: JSON.stringify({ slots }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ciclo-secciones', cycleId] });
      queryClient.invalidateQueries({ queryKey: ['ciclos'] });
    },
  });
}
