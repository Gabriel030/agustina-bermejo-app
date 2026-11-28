import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useReplaceSectionPreceptors(cycleId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ sectionId, personIds }) =>
      apiFetch(`/preceptor-assignments/subject-sections/${sectionId}`, {
        method: 'PUT',
        body: JSON.stringify({ personIds }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ciclo-secciones', cycleId] });
    },
  });
}
