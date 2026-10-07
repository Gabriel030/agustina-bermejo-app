import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useExcepcionesPendientes() {
  return useQuery({
    queryKey: ['director-approval-requests', 'REQUESTED'],
    queryFn: () => apiFetch('/director-approval-requests'),
  });
}

export function useHistorialExcepciones() {
  const aprobadas = useQuery({
    queryKey: ['director-approval-requests', 'APPROVED'],
    queryFn: () => apiFetch('/director-approval-requests?status=APPROVED'),
  });
  const rechazadas = useQuery({
    queryKey: ['director-approval-requests', 'REJECTED'],
    queryFn: () => apiFetch('/director-approval-requests?status=REJECTED'),
  });
  const cargando = aprobadas.isLoading || rechazadas.isLoading;
  const historial = [...(aprobadas.data ?? []), ...(rechazadas.data ?? [])].sort(
    (a, b) => new Date(b.decidedAt ?? 0) - new Date(a.decidedAt ?? 0),
  );
  return { data: historial, isLoading: cargando, isError: aprobadas.isError || rechazadas.isError };
}

export function useExcepcion(id) {
  return useQuery({
    queryKey: ['director-approval-requests', 'detalle', id],
    queryFn: () => apiFetch(`/director-approval-requests/${id}`),
    enabled: !!id,
  });
}

export function useAprobarExcepcion(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (notas) => apiFetch(`/director-approval-requests/${id}/approve`, { method: 'POST', body: JSON.stringify({ notas }) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['director-approval-requests'] }),
  });
}

export function useRechazarExcepcion(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (notas) => apiFetch(`/director-approval-requests/${id}/reject`, { method: 'POST', body: JSON.stringify({ notas }) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['director-approval-requests'] }),
  });
}
