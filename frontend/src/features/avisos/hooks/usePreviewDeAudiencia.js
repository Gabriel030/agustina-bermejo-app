import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { rutaDeSeccion } from './useAvisos';

const DEBOUNCE_MS = 300;

export function usePreviewDeAudiencia(sectionId, tipo, spec) {
  const [specDebounceada, setSpecDebounceada] = useState(spec);
  const serializada = JSON.stringify(spec);

  useEffect(() => {
    const id = setTimeout(() => setSpecDebounceada(JSON.parse(serializada)), DEBOUNCE_MS);
    return () => clearTimeout(id);
  }, [serializada]);

  const base = rutaDeSeccion(tipo);
  const query = useQuery({
    queryKey: [base, sectionId, 'audience-preview', JSON.stringify(specDebounceada)],
    queryFn: () =>
      apiFetch(`/${base}/${sectionId}/audience-preview`, {
        method: 'POST',
        body: JSON.stringify(specDebounceada),
      }),
    enabled: !!sectionId && !!specDebounceada?.key && tipo !== 'COURSE',
    placeholderData: (anterior) => anterior,
  });

  return { ...query, recalculando: serializada !== JSON.stringify(specDebounceada) || query.isFetching };
}
