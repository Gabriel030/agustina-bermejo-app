import { useCallback } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const ACCESS_KEY = ['program-enrollments', 'equivalences', 'access'];
const DEFAULT_ACCESS = { record: true, void: true };

export function useEquivalenceAccess() {
  const queryClient = useQueryClient();
  const { data } = useQuery({ queryKey: ACCESS_KEY, queryFn: () => DEFAULT_ACCESS, initialData: DEFAULT_ACCESS, staleTime: Infinity, gcTime: Infinity });
  const deny = useCallback(
    (capability) =>
      queryClient.setQueryData(ACCESS_KEY, (current) => {
        const base = current ?? DEFAULT_ACCESS;
        return base[capability] === false ? base : { ...base, [capability]: false };
      }),
    [queryClient],
  );
  return { ...data, deny };
}

function useInvalidateEquivalenceEffects(programEnrollmentId) {
  const queryClient = useQueryClient();
  return useCallback(
    () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['program-enrollments', programEnrollmentId, 'equivalences'] }),
        queryClient.invalidateQueries({ queryKey: ['program-enrollments', programEnrollmentId, 'progress'] }),
      ]),
    [queryClient, programEnrollmentId],
  );
}

export function useRecordSubjectEquivalence(programEnrollmentId) {
  const { deny } = useEquivalenceAccess();
  const invalidate = useInvalidateEquivalenceEffects(programEnrollmentId);
  return useMutation({
    mutationFn: (payload) => apiFetch(`/program-enrollments/${programEnrollmentId}/equivalences`, { method: 'POST', body: JSON.stringify(payload) }),
    onSuccess: invalidate,
    onError: (error) => {
      if (error?.status === 403) deny('record');
      if (error?.status === 409) invalidate();
    },
  });
}

export function useVoidSubjectEquivalence(programEnrollmentId) {
  const { deny } = useEquivalenceAccess();
  const invalidate = useInvalidateEquivalenceEffects(programEnrollmentId);
  return useMutation({
    mutationFn: ({ equivalenceId, reason }) =>
      apiFetch(`/program-enrollments/${programEnrollmentId}/equivalences/${equivalenceId}/void`, { method: 'POST', body: JSON.stringify({ reason }) }),
    onSuccess: invalidate,
    onError: (error) => {
      if (error?.status === 403) deny('void');
      if (error?.status === 409) invalidate();
    },
  });
}
