import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

// Épica 3 Fase 5 (Docente · configuración de evaluación y mesas).

export function useMisComisiones() {
  return useQuery({ queryKey: ['me', 'section-evaluation-configs'], queryFn: () => apiFetch('/me/section-evaluation-configs') });
}

export function useComisionesDictadas(habilitado = true) {
  return useQuery({
    queryKey: ['me', 'teaching-sections'],
    queryFn: () => apiFetch('/me/teaching-sections'),
    enabled: habilitado,
  });
}

export function useConfiguracionComision(sectionId) {
  return useQuery({
    queryKey: ['me', 'section-evaluation-configs', sectionId],
    queryFn: () => apiFetch(`/me/section-evaluation-configs/${sectionId}`),
    enabled: !!sectionId,
  });
}

export function useGuardarBorradorComision(sectionId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) =>
      apiFetch(`/me/section-evaluation-configs/${sectionId}`, { method: 'PUT', body: JSON.stringify(payload) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me', 'section-evaluation-configs', sectionId] });
      queryClient.invalidateQueries({ queryKey: ['me', 'section-evaluation-configs'] });
    },
  });
}

export function useEnviarComisionAAprobacion(sectionId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch(`/me/section-evaluation-configs/${sectionId}/submit`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me', 'section-evaluation-configs', sectionId] });
      queryClient.invalidateQueries({ queryKey: ['me', 'section-evaluation-configs'] });
    },
  });
}

export function useHolidays() {
  return useQuery({ queryKey: ['holidays'], queryFn: () => apiFetch('/holidays'), staleTime: 1000 * 60 * 60 });
}
