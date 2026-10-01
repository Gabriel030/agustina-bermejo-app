import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { rutaDeSeccion } from './useAvisos';

export function useCrearAviso(sectionId, tipo) {
  const queryClient = useQueryClient();
  const base = rutaDeSeccion(tipo);
  return useMutation({
    mutationFn: (data) =>
      apiFetch(`/${base}/${sectionId}/announcements`, {
        method: 'POST',
        body: JSON.stringify(data),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [base, sectionId, 'announcements'] }),
  });
}
