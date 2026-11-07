import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export const TERMS_KEY = ['annual-upfront-terms'];

export function useCurrentTerms(enabled = true) {
  return useQuery({
    queryKey: [...TERMS_KEY, 'current'],
    queryFn: () => apiFetch('/annual-upfront-terms/current'),
    enabled,
  });
}

export function useTermsVersion(versionNumber) {
  return useQuery({
    queryKey: [...TERMS_KEY, 'version', versionNumber],
    queryFn: () => apiFetch(`/annual-upfront-terms/versions/${versionNumber}`),
    enabled: versionNumber != null,
  });
}

export function useTermsVersions() {
  return useQuery({
    queryKey: [...TERMS_KEY, 'versions'],
    queryFn: () => apiFetch('/annual-upfront-terms/versions'),
  });
}

export function usePublishTerms() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (text) => apiFetch('/annual-upfront-terms/versions', { method: 'POST', body: JSON.stringify({ text }) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: TERMS_KEY }),
  });
}
