import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useUpdateStudent(studentId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => apiFetch(`/students/${studentId}`, { method: 'PUT', body: JSON.stringify(body) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alumno', studentId], exact: true });
      queryClient.invalidateQueries({ queryKey: ['alumnos'], refetchType: 'none' });
    },
  });
}
