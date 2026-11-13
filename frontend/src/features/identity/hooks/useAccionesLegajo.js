import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useWithdrawStudent(studentId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ reason, detail }) =>
      apiFetch(`/students/${studentId}/withdraw`, {
        method: 'POST',
        body: JSON.stringify({ reason, detail }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alumno', studentId], exact: true });
      queryClient.invalidateQueries({ queryKey: ['alumnos'], refetchType: 'none' });
    },
  });
}

export function useReactivateStudent(studentId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (reason) =>
      apiFetch(`/students/${studentId}/reactivate`, {
        method: 'POST',
        body: JSON.stringify({ reason }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alumno', studentId], exact: true });
      queryClient.invalidateQueries({ queryKey: ['alumnos'], refetchType: 'none' });
    },
  });
}

export function useResetStudentPassword(studentId) {
  return useMutation({
    mutationFn: () =>
      apiFetch(`/students/${studentId}/reset-password`, { method: 'POST' }),
  });
}
