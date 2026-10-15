import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useMesas(status) {
  const params = status ? `?status=${status}` : '';
  return useQuery({ queryKey: ['exam-boards', status ?? 'ALL'], queryFn: () => apiFetch(`/exam-boards${params}`) });
}

export function useMesa(id) {
  return useQuery({ queryKey: ['exam-boards', 'detalle', id], queryFn: () => apiFetch(`/exam-boards/${id}`), enabled: !!id });
}

export function useAnotados(id) {
  return useQuery({
    queryKey: ['exam-boards', id, 'registrations'],
    queryFn: () => apiFetch(`/exam-boards/${id}/registrations`),
    enabled: !!id,
  });
}

export function useInstructores() {
  return useQuery({ queryKey: ['instructors'], queryFn: () => apiFetch('/instructors') });
}

function invalidarMesa(queryClient, id) {
  queryClient.invalidateQueries({ queryKey: ['exam-boards', 'detalle', id] });
  queryClient.invalidateQueries({ queryKey: ['exam-boards', id, 'registrations'] });
  queryClient.invalidateQueries({ queryKey: ['exam-boards', 'ALL'] });
}

export function useAsignarTribunal(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (instructorId) => apiFetch(`/exam-boards/${id}/tribunal`, { method: 'POST', body: JSON.stringify({ instructorId }) }),
    onSuccess: () => invalidarMesa(queryClient, id),
  });
}

export function useCerrarActa(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch(`/exam-boards/${id}/close`, { method: 'POST' }),
    onSuccess: () => invalidarMesa(queryClient, id),
  });
}

export function useDarDeBajaInscripcion(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (registrationId) => apiFetch(`/exam-board-registrations/${registrationId}/withdraw`, { method: 'POST' }),
    onSuccess: () => invalidarMesa(queryClient, id),
  });
}
