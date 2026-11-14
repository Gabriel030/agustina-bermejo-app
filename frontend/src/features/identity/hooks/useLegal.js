import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export const LEGAL_KEYS = {
  current: ['legal', 'documents', 'current'],
  pending: ['me', 'legal-acceptances', 'pending'],
  newsletter: ['me', 'newsletter-preference'],
};

export function useCurrentLegalDocuments() {
  return useQuery({
    queryKey: LEGAL_KEYS.current,
    queryFn: () => apiFetch('/legal/documents/current'),
    staleTime: 5 * 60 * 1000,
  });
}

export function usePendingLegalAcceptances(enabled = true) {
  return useQuery({
    queryKey: LEGAL_KEYS.pending,
    queryFn: () => apiFetch('/me/legal-acceptances/pending'),
    enabled,
    retry: false,
  });
}

export function useAcceptLegalDocuments() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch('/me/legal-acceptances', { method: 'POST' }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: LEGAL_KEYS.pending }),
  });
}

export function useResendGuardianConfirmation() {
  return useMutation({
    mutationFn: () => apiFetch('/me/legal-acceptances/guardian-confirmation/resend', { method: 'POST' }),
  });
}

export function useConfirmGuardian() {
  return useMutation({
    mutationFn: (token) =>
      apiFetch('/auth/legal/guardian-confirmation', { method: 'POST', body: JSON.stringify({ token }) }),
  });
}

export function useNewsletterPreference() {
  return useQuery({
    queryKey: LEGAL_KEYS.newsletter,
    queryFn: () => apiFetch('/me/newsletter-preference'),
  });
}

export function useUpdateNewsletterPreference() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (optedIn) =>
      apiFetch('/me/newsletter-preference', { method: 'PUT', body: JSON.stringify({ optedIn }) }),
    onSuccess: (preference) => queryClient.setQueryData(LEGAL_KEYS.newsletter, preference),
  });
}
