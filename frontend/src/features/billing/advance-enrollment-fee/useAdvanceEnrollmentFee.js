import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useRequestAdvanceEnrollmentFee(enrollmentId, scope) {
  const queryClient = useQueryClient();
  const prefix = scope === 'student' ? '/me' : '';
  return useMutation({
    mutationFn: () => apiFetch(`${prefix}/program-enrollments/${enrollmentId}/advance-enrollment-fee`, { method: 'POST' }),
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['me'] }),
        queryClient.invalidateQueries({ queryKey: ['program-enrollment'] }),
        queryClient.invalidateQueries({ queryKey: ['students'] }),
      ]),
  });
}
