import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useEgresosPendientes() {
  return useQuery({
    queryKey: ['graduation-requests', 'ADMIN_VERIFIED'],
    queryFn: () => apiFetch('/graduation-requests?status=ADMIN_VERIFIED'),
  });
}

export function useHistorialEgresos() {
  const aprobados = useQuery({
    queryKey: ['graduation-requests', 'APPROVED'],
    queryFn: () => apiFetch('/graduation-requests?status=APPROVED'),
  });
  const rechazados = useQuery({
    queryKey: ['graduation-requests', 'REJECTED'],
    queryFn: () => apiFetch('/graduation-requests?status=REJECTED'),
  });
  const cargando = aprobados.isLoading || rechazados.isLoading;
  const historial = [...(aprobados.data ?? []), ...(rechazados.data ?? [])].sort(
    (a, b) => new Date(b.decidedAt ?? 0) - new Date(a.decidedAt ?? 0),
  );
  return { data: historial, isLoading: cargando, isError: aprobados.isError || rechazados.isError };
}

export function useEgreso(id) {
  return useQuery({
    queryKey: ['graduation-requests', 'detalle', id],
    queryFn: () => apiFetch(`/graduation-requests/${id}`),
    enabled: !!id,
  });
}

export function useAprobarEgreso(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch(`/graduation-requests/${id}/approve`, { method: 'POST' }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['graduation-requests'] }),
  });
}

export function useRechazarEgreso(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (motivo) => apiFetch(`/graduation-requests/${id}/reject`, { method: 'POST', body: JSON.stringify({ motivo }) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['graduation-requests'] }),
  });
}

export function useMiSolicitudEgreso(programEnrollmentId) {
  return useQuery({
    queryKey: ['me', 'program-enrollments', programEnrollmentId, 'graduation-request'],
    queryFn: () => apiFetch(`/me/program-enrollments/${programEnrollmentId}/graduation-request`),
    enabled: !!programEnrollmentId,
  });
}

export function useSolicitarEgreso(programEnrollmentId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch(`/me/program-enrollments/${programEnrollmentId}/graduation-request`, { method: 'POST' }),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['me', 'program-enrollments'] });
      queryClient.invalidateQueries({ queryKey: ['graduation-requests'] });
    },
  });
}

export function useEgresosPorVerificar() {
  return useQuery({
    queryKey: ['graduation-requests', 'REQUESTED'],
    queryFn: () => apiFetch('/graduation-requests?status=REQUESTED'),
  });
}

const VERIFICATION_HISTORY_STATUSES = ['ADMIN_VERIFIED', 'APPROVED', 'REJECTED'];

export function useHistorialVerificacionEgresos() {
  const queries = useQueries({
    queries: VERIFICATION_HISTORY_STATUSES.map((status) => ({
      queryKey: ['graduation-requests', status],
      queryFn: () => apiFetch(`/graduation-requests?status=${status}`),
    })),
  });
  const data = queries
    .flatMap((query) => query.data ?? [])
    .sort((a, b) => new Date(b.decidedAt ?? b.requestedAt ?? 0) - new Date(a.decidedAt ?? a.requestedAt ?? 0));
  return {
    data,
    isLoading: queries.some((query) => query.isLoading),
    isError: queries.some((query) => query.isError),
    error: queries.find((query) => query.error)?.error ?? null,
    refetch: () => queries.forEach((query) => query.refetch()),
  };
}

export function useGraduationDebtSummary(id) {
  return useQuery({
    queryKey: ['graduation-requests', 'debt-summary', id],
    queryFn: () => apiFetch(`/graduation-requests/${id}/debt-summary`),
    enabled: !!id,
  });
}

export function useVerificarDeudaEgreso(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch(`/graduation-requests/${id}/verify-debt`, { method: 'POST' }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['graduation-requests'] }),
  });
}
