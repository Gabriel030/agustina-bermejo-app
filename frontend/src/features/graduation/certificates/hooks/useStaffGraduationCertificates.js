import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiFetchBlob } from '@/api/client';

const CERTIFICATES_KEY = ['graduation-certificates'];
const PAGE_SIZE = 20;

export function useEligibleGraduates({ kind, query, page }) {
  const trimmedQuery = query.trim();
  const params = new URLSearchParams({ kind, page: String(page - 1), size: String(PAGE_SIZE) });
  if (trimmedQuery) params.set('query', trimmedQuery);
  return useQuery({
    queryKey: [...CERTIFICATES_KEY, 'eligible', kind, trimmedQuery, page],
    queryFn: () => apiFetch(`/graduation-certificates/eligible?${params}`),
    placeholderData: keepPreviousData,
  });
}

export function useIssueGraduationBatch() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (items) => apiFetch('/graduation-certificates/batch', { method: 'POST', body: JSON.stringify({ items }) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: CERTIFICATES_KEY }),
  });
}

export function usePreviewGraduationCertificate() {
  return useMutation({
    mutationFn: (item) => apiFetchBlob('/graduation-certificates/preview', { method: 'POST', body: JSON.stringify(item) }),
  });
}
