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
      queryClient.invalidateQueries({ queryKey: ['alumno', studentId] });
      queryClient.invalidateQueries({ queryKey: ['alumnos'] });
    },
  });
}

export function useResetStudentPassword(studentId) {
  return useMutation({
    mutationFn: () =>
      apiFetch(`/students/${studentId}/reset-password`, { method: 'POST' }),
  });
}
