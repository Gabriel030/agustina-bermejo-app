import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useEvaluationSchemes() {
  return useQuery({ queryKey: ['evaluation-schemes'], queryFn: () => apiFetch('/evaluation-schemes') });
}

export function useCrearEsquema() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) => apiFetch('/evaluation-schemes', { method: 'POST', body: JSON.stringify(payload) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['evaluation-schemes'] }),
  });
}

export function useEditarEsquema(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) => apiFetch(`/evaluation-schemes/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['evaluation-schemes'] }),
  });
}

export function useConfigsPendientes() {
  return useQuery({
    queryKey: ['section-evaluation-configs', 'PENDING_APPROVAL'],
    queryFn: () => apiFetch('/section-evaluation-configs?status=PENDING_APPROVAL'),
  });
}

export function useHistorialAprobaciones() {
  const aprobadas = useQuery({
    queryKey: ['section-evaluation-configs', 'APPROVED'],
    queryFn: () => apiFetch('/section-evaluation-configs?status=APPROVED'),
  });
  const rechazadas = useQuery({
    queryKey: ['section-evaluation-configs', 'REJECTED'],
    queryFn: () => apiFetch('/section-evaluation-configs?status=REJECTED'),
  });
  const cargando = aprobadas.isLoading || rechazadas.isLoading;
  const historial = [...(aprobadas.data ?? []), ...(rechazadas.data ?? [])].sort(
    (a, b) => new Date(b.reviewedAt ?? 0) - new Date(a.reviewedAt ?? 0),
  );
  return { data: historial, isLoading: cargando };
}

export function useConfiguracionComisionStaff(sectionId) {
  return useQuery({
    queryKey: ['section-evaluation-configs', sectionId],
    queryFn: () => apiFetch(`/section-evaluation-configs/${sectionId}`),
    enabled: !!sectionId,
  });
}

export function useAprobarConfig(sectionId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch(`/section-evaluation-configs/${sectionId}/approve`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['section-evaluation-configs'] });
    },
  });
}

export function useRechazarConfig(sectionId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (reason) =>
      apiFetch(`/section-evaluation-configs/${sectionId}/reject`, { method: 'POST', body: JSON.stringify({ reason }) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['section-evaluation-configs'] });
    },
  });
}
