import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useSubjects(status) {
  const qs = status ? `?status=${status}` : '';
  return useQuery({
    queryKey: ['subjects', { status }],
    queryFn: () => apiFetch(`/subjects${qs}`),
  });
}

export function useSubject(id) {
  return useQuery({
    queryKey: ['subjects', id],
    queryFn: () => apiFetch(`/subjects/${id}`),
    enabled: !!id,
  });
}

export function useCreateSubject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => apiFetch('/subjects', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['subjects'] }),
  });
}

export function useUpdateSubject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...body }) => apiFetch(`/subjects/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['subjects'] }),
  });
}

export function useSubmitForApproval() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/subjects/${id}/submit-for-approval`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['subjects'] }),
  });
}

export function useApproveSubject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/subjects/${id}/approve`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['subjects'] }),
  });
}

export function useRejectSubject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }) => apiFetch(`/subjects/${id}/reject`, { method: 'POST', body: JSON.stringify({ reason }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['subjects'] }),
  });
}

export function useDeactivateSubject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/subjects/${id}/deactivate`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['subjects'] }),
  });
}

export function useActivateSubject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/subjects/${id}/activate`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['subjects'] }),
  });
}
