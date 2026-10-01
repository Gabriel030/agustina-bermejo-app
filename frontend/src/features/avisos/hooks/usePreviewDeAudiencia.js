import { useMutation } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { rutaDeSeccion } from './useAvisos';

export function usePreviewDeAudiencia(sectionId, tipo) {
  const base = rutaDeSeccion(tipo);
  return useMutation({
    mutationFn: (audienceSpec) =>
      apiFetch(`/${base}/${sectionId}/audience-preview`, {
        method: 'POST',
        body: JSON.stringify(audienceSpec),
      }),
  });
}
