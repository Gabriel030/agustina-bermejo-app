import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useProspectosList() {
  return useQuery({
    queryKey: ['students'],
    queryFn: () => apiFetch('/prospects'),
  });
}

export function useCrearProspecto() {
  return useMutation({
    mutationFn: (datos) =>
      apiFetch('/prospects', { method: 'POST', body: JSON.stringify(datos) }),
  });
}

export function useSubirDocumentoProspecto() {
  return useMutation({
    mutationFn: ({ studentId, type, file }) => {
      const form = new FormData();
      form.append('file', file);
      return apiFetch(`/prospects/${studentId}/documents?type=${type}`, {
        method: 'POST',
        body: form,
      });
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
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['students'] });
    },
  });
}

export function useReenviarVerificacion() {
  return useMutation({
    mutationFn: (studentId) =>
      apiFetch(`/prospects/${studentId}/send-verification`, { method: 'POST' }),
  });
}
