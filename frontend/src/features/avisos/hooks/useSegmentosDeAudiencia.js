import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { rutaDeSeccion } from './useAvisos';

export function useSegmentosDeAudiencia(sectionId, tipo) {
  const base = rutaDeSeccion(tipo);
  return useQuery({
    queryKey: [base, sectionId, 'audience-segments'],
    queryFn: () => apiFetch(`/${base}/${sectionId}/audience-segments`),
    enabled: !!sectionId && tipo !== 'COURSE',
  });
}
