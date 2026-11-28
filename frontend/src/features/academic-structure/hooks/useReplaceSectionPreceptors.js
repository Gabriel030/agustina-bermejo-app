import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useReplaceSectionPreceptors(cycleId, sectionKind = 'subject-sections') {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ sectionId, personIds }) =>
      apiFetch(`/preceptor-assignments/${sectionKind}/${sectionId}`, {
        method: 'PUT',
        body: JSON.stringify({ personIds }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ciclo-secciones', cycleId] });
    },
  });
}
