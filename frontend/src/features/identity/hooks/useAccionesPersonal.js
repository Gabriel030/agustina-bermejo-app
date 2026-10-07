import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useCrearPersonal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data) =>
      apiFetch('/staff', { method: 'POST', body: JSON.stringify(data) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['personal'] });
    },
  });
}

export function useEditarPersonal(personId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data) =>
      apiFetch(`/staff/${personId}`, { method: 'PUT', body: JSON.stringify(data) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['personal'] });
      queryClient.invalidateQueries({ queryKey: ['personal', personId] });
    },
  });
}

export function useWithdrawPersonal(personId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch(`/staff/${personId}/withdraw`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['personal'] });
      queryClient.invalidateQueries({ queryKey: ['personal', personId] });
    },
  });
}

export function useReactivarPersonal(personId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch(`/staff/${personId}/reactivate`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['personal'] });
      queryClient.invalidateQueries({ queryKey: ['personal', personId] });
    },
  });
}

export function useResetPersonalPassword(personId) {
  return useMutation({
    mutationFn: () =>
      apiFetch(`/staff/${personId}/reset-password`, { method: 'POST' }),
  });
}
