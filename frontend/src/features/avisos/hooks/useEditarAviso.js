import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { rutaDeSeccion } from './useAvisos';

export function useEditarAviso(sectionId, tipo, announcementId) {
  const queryClient = useQueryClient();
  const base = rutaDeSeccion(tipo);
  return useMutation({
    mutationFn: (data) =>
      apiFetch(`/announcements/${announcementId}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [base, sectionId, 'announcements'] }),
  });
}
