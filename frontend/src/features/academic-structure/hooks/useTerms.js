import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const TERMS_KEY = ['terms'];

export function useAllTerms() {
  return useQuery({ queryKey: [...TERMS_KEY, 'all'], queryFn: () => apiFetch('/terms') });
}

function useInvalidateTerms() {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: TERMS_KEY });
    queryClient.invalidateQueries({ queryKey: ['terminos'] });
  };
}

export function useCreateTerm() {
  const invalidate = useInvalidateTerms();
  return useMutation({
    mutationFn: (body) => apiFetch('/terms', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: invalidate,
  });
}

export function useUpdateTerm() {
  const invalidate = useInvalidateTerms();
  return useMutation({
    mutationFn: ({ termId, ...body }) => apiFetch(`/terms/${termId}`, { method: 'PUT', body: JSON.stringify(body) }),
    onSuccess: invalidate,
  });
}

export function useTermAction() {
  const invalidate = useInvalidateTerms();
  return useMutation({
    mutationFn: ({ termId, path }) => apiFetch(`/terms/${termId}/${path}`, { method: 'POST' }),
    onSuccess: invalidate,
  });
}
