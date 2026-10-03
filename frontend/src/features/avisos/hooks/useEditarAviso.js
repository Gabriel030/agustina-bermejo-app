import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { rutaDeSeccion } from './useAvisos';

export function useEditarAviso(sectionId, tipo, announcementId) {
  const queryClient = useQueryClient();
  const base = rutaDeSeccion(tipo);
  return useMutation({
    mutationFn: (payload) =>
      apiFetch(`/announcements/${announcementId}`, { method: 'PUT', body: JSON.stringify(payload) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [base, sectionId, 'announcements'] });
      queryClient.invalidateQueries({ queryKey: ['announcements', announcementId, 'recipients'] });
    },
  });
}
