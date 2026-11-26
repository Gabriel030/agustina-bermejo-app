import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { CERTIFICATES_KEY } from '@/features/graduation/certificates/hooks/useStaffGraduationCertificates';
import { REVIEW_PAGE_SIZE } from '@/features/graduation/certificates/lib/importedReview';

export const REVIEWS_KEY = ['graduation-certificate-reviews'];

export function useImportedReviews({ status, query, page, enabled }) {
  const trimmedQuery = query.trim();
  const params = new URLSearchParams({ status, page: String(page - 1), size: String(REVIEW_PAGE_SIZE) });
  if (trimmedQuery) params.set('query', trimmedQuery);
  return useQuery({
    queryKey: [...REVIEWS_KEY, 'list', status, trimmedQuery, page],
    queryFn: () => apiFetch(`/graduation-certificate-reviews?${params}`),
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function usePendingImportedReviewCount({ enabled }) {
  return useQuery({
    queryKey: [...REVIEWS_KEY, 'pending-count'],
    queryFn: () => apiFetch('/graduation-certificate-reviews?status=PENDING_REVIEW&page=0&size=1'),
    select: (page) => page.totalElements ?? 0,
    enabled,
  });
}

function useInvalidateReviews() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: REVIEWS_KEY }),
      queryClient.invalidateQueries({ queryKey: CERTIFICATES_KEY }),
    ]);
}

export function useEnableImportedReview() {
  const invalidate = useInvalidateReviews();
  return useMutation({
    mutationFn: (graduationRequestId) =>
      apiFetch(`/graduation-certificate-reviews/${graduationRequestId}/enable`, { method: 'POST' }),
    onSettled: invalidate,
  });
}

export function useDisableImportedReview() {
  const invalidate = useInvalidateReviews();
  return useMutation({
    mutationFn: ({ graduationRequestId, reason }) =>
      apiFetch(`/graduation-certificate-reviews/${graduationRequestId}/disable`, {
        method: 'POST',
        body: JSON.stringify({ reason }),
      }),
    onSettled: invalidate,
  });
}

export function useDecideImportedReviewBatch() {
  const invalidate = useInvalidateReviews();
  return useMutation({
    mutationFn: ({ graduationRequestIds, decision, reason }) =>
      apiFetch('/graduation-certificate-reviews/batch', {
        method: 'POST',
        body: JSON.stringify({ graduationRequestIds, decision, ...(reason ? { reason } : {}) }),
      }),
    onSettled: invalidate,
  });
}
