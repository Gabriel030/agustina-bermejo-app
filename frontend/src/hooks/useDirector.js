import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useFinancialDashboard() {
  return useQuery({
    queryKey: ['financial-dashboard'],
    queryFn: () => apiFetch('/financial-dashboard'),
  });
}

export function useDebtors(sortBy = 'DEBT_AGE') {
  return useQuery({
    queryKey: ['financial-dashboard', 'debtors', sortBy],
    queryFn: () => apiFetch(`/financial-dashboard/debtors?sortBy=${sortBy}`),
  });
}

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

export function useEditarEsquema() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }) => apiFetch(`/evaluation-schemes/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
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
  return { data: historial, isLoading: cargando };
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
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['graduation-requests'] }),
  });
}

export function useRechazarEgreso(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (motivo) => apiFetch(`/graduation-requests/${id}/reject`, { method: 'POST', body: JSON.stringify({ motivo }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['graduation-requests'] }),
  });
}

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
  return { data: historial, isLoading: cargando };
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
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['director-approval-requests'] }),
  });
}

export function useRechazarExcepcion(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (notas) => apiFetch(`/director-approval-requests/${id}/reject`, { method: 'POST', body: JSON.stringify({ notas }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['director-approval-requests'] }),
  });
}

export function useProgresoStaff(programEnrollmentId) {
  return useQuery({
    queryKey: ['program-enrollments', programEnrollmentId, 'progress'],
    queryFn: () => apiFetch(`/program-enrollments/${programEnrollmentId}/progress`),
    enabled: !!programEnrollmentId,
  });
}

export function useAsistenciaPorMateria(programEnrollmentId) {
  return useQuery({
    queryKey: ['program-enrollments', programEnrollmentId, 'attendance-summary'],
    queryFn: () => apiFetch(`/program-enrollments/${programEnrollmentId}/attendance-summary`),
    enabled: !!programEnrollmentId,
  });
}

export function useEstadoDePago(studentId) {
  return useQuery({
    queryKey: ['students', studentId, 'billing-overview'],
    queryFn: () => apiFetch(`/students/${studentId}/billing-overview`),
    enabled: !!studentId,
    select: (entries) => ({
      standing: entries.some((e) => e.billingStanding !== 'UP_TO_DATE') ? 'MOROSO' : 'AL_DIA',
    }),
  });
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
