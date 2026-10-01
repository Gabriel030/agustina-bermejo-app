import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { rutaDeSeccion } from './useAvisos';

export function useEliminarAviso(sectionId, tipo) {
  const queryClient = useQueryClient();
  const base = rutaDeSeccion(tipo);
  return useMutation({
    mutationFn: (announcementId) => apiFetch(`/announcements/${announcementId}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [base, sectionId, 'announcements'] }),
  });
}
