import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiFetchBlob } from '@/api/client';

function basePath(type) {
  return type === 'COURSE' ? 'course-sections' : 'program-sections';
}

export function useMisComisionesParaAsistencia() {
  return useQuery({ queryKey: ['me', 'teaching-sections'], queryFn: () => apiFetch('/me/teaching-sections') });
}

export function useRosterDeAsistencia(type, sectionId) {
  return useQuery({
    queryKey: ['attendance', 'roster', type, sectionId],
    queryFn: () => apiFetch(`/${basePath(type)}/${sectionId}/roster`),
    enabled: !!sectionId,
  });
}

export function useGuardarAsistencia(type, sectionId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) => apiFetch(`/${basePath(type)}/${sectionId}/attendance`, { method: 'POST', body: JSON.stringify(payload) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['attendance', 'roster', type, sectionId] });
      queryClient.invalidateQueries({ queryKey: ['attendance', 'history', type, sectionId] });
      queryClient.invalidateQueries({ queryKey: ['attendance', 'absence-report', type, sectionId] });
      queryClient.invalidateQueries({ queryKey: ['attendance', 'class-session', type, sectionId] });
    },
  });
}

export function useClaseDelDia(type, sectionId, classDate) {
  return useQuery({
    queryKey: ['attendance', 'class-session', type, sectionId, classDate],
    queryFn: () => apiFetch(`/${basePath(type)}/${sectionId}/class-sessions/${classDate}`),
    enabled: !!sectionId && !!classDate,
  });
}

export function useCancelarClase(type, sectionId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (classDate) => apiFetch(`/${basePath(type)}/${sectionId}/class-sessions/${classDate}/cancel`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['attendance', 'history', type, sectionId] });
      queryClient.invalidateQueries({ queryKey: ['attendance', 'absence-report', type, sectionId] });
      queryClient.invalidateQueries({ queryKey: ['attendance', 'class-session', type, sectionId] });
    },
  });
}

export function useHistorialAsistencia(type, sectionId) {
  return useQuery({
    queryKey: ['attendance', 'history', type, sectionId],
    queryFn: () => apiFetch(`/${basePath(type)}/${sectionId}/attendance/history`),
    enabled: !!sectionId,
  });
}

export function useReporteAusencias(type, sectionId) {
  return useQuery({
    queryKey: ['attendance', 'absence-report', type, sectionId],
    queryFn: () => apiFetch(`/${basePath(type)}/${sectionId}/attendance/absence-report`),
    enabled: !!sectionId,
  });
}

export function useSolicitarModificacion(attendanceId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) => apiFetch(`/attendance/${attendanceId}/change-requests`, { method: 'POST', body: JSON.stringify(payload) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me', 'attendance-change-requests'] });
    },
  });
}

export async function exportarAsistencia(type, sectionId, { format, scope, classDate }) {
  const params = new URLSearchParams({ format, scope });
  if (classDate) params.set('classDate', classDate);
  const { blob, filename } = await apiFetchBlob(`/${basePath(type)}/${sectionId}/attendance/export?${params}`);
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
