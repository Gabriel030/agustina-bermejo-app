import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export const PROSPECTS_PAGE_SIZE = 25;

export function useStaffProspects({ search, page = 0, size = PROSPECTS_PAGE_SIZE } = {}) {
  return useQuery({
    queryKey: ['prospects', 'list', { search, page, size }],
    queryFn: () => {
      const params = new URLSearchParams();
      if (search) params.set('search', search);
      params.set('page', String(page));
      params.set('size', String(size));
      return apiFetch(`/prospects?${params.toString()}`);
    },
    placeholderData: (previous) => previous,
  });
}

export function useCrearProspecto() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ nombre, apellido, email, telefono }) => {
      const institutions = await apiFetch('/institutions');
      const institutionId = institutions[0]?.id;
      return apiFetch('/prospects/by-staff', {
        method: 'POST',
        body: JSON.stringify({ nombre, apellido, email, telefono: telefono ?? '', institutionId }),
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['prospects'] });
    },
  });
}

export function useSubirDocumentoProspecto() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ studentId, type, file }) => {
      const form = new FormData();
      form.append('file', file);
      return apiFetch(`/prospects/${studentId}/documents?type=${type}`, {
        method: 'POST',
        body: form,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['prospects'] });
    },
  });
}

export function useDocumentosProspecto(studentId) {
  return useQuery({
    queryKey: ['prospects', studentId, 'documents'],
    queryFn: () => apiFetch(`/prospects/${studentId}/documents`),
    enabled: !!studentId,
  });
}

export function useGuardarBorradorProspecto() {
  return useMutation({
    mutationFn: ({ studentId, datos }) =>
      apiFetch(`/prospects/${studentId}`, { method: 'PUT', body: JSON.stringify(datos) }),
  });
}

export function useConfirmarAlta() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ studentId, datos }) =>
      apiFetch(`/prospects/${studentId}/confirm`, {
        method: 'POST',
        body: JSON.stringify(datos),
      }),
    onSuccess: (_data, { studentId }) => {
      queryClient.invalidateQueries({ queryKey: ['students', studentId] });
      queryClient.invalidateQueries({ queryKey: ['alumno', studentId], exact: true });
      queryClient.invalidateQueries({ queryKey: ['alumnos'], refetchType: 'none' });
      queryClient.invalidateQueries({ queryKey: ['prospects', 'list'] });
    },
  });
}

export function useReenviarVerificacion() {
  return useMutation({
    mutationFn: (studentId) =>
      apiFetch(`/prospects/${studentId}/send-verification`, { method: 'POST' }),
  });
}
