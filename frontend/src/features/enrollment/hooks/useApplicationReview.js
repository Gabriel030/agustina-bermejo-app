import { useRef } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiFetchBlob } from '@/api/client';
import { newIdempotencyKey } from '@/api/idempotencyKey';

const ROOT = 'program-applications-review';
export const HISTORY_PAGE_SIZE = 10;

export function usePendingApplications() {
  return useQuery({
    queryKey: [ROOT, 'pending'],
    queryFn: () => apiFetch('/program-enrollment-applications/pending'),
  });
}

export function useApplicationHistory({ status, search, page }) {
  const params = new URLSearchParams({ page: String(page), size: String(HISTORY_PAGE_SIZE) });
  if (status && status !== 'ALL') params.set('status', status);
  if (search) params.set('search', search);
  return useQuery({
    queryKey: [ROOT, 'history', { status: status ?? 'ALL', search: search ?? '', page }],
    queryFn: () => apiFetch(`/program-enrollment-applications/history?${params.toString()}`),
    placeholderData: keepPreviousData,
  });
}

export function useApplicationForReview(id) {
  return useQuery({
    queryKey: [ROOT, 'detail', id],
    queryFn: () => apiFetch(`/program-enrollment-applications/${id}`),
    enabled: !!id,
  });
}

export function useApprovalCheck(id, enabled) {
  return useQuery({
    queryKey: [ROOT, 'approval-check', id],
    queryFn: () => apiFetch(`/program-enrollment-applications/${id}/approval-check`),
    enabled: !!id && enabled,
    staleTime: 0,
  });
}

function useDecisionKey() {
  const attempt = useRef({ signature: null, key: null });
  return {
    keyFor(signature) {
      if (attempt.current.signature !== signature) {
        attempt.current = { signature, key: newIdempotencyKey() };
      }
      return attempt.current.key;
    },
    reset() {
      attempt.current = { signature: null, key: null };
    },
  };
}

function useApplicationDecision(id, action, buildBody) {
  const queryClient = useQueryClient();
  const decisionKey = useDecisionKey();
  return useMutation({
    mutationFn: (reason) => {
      const body = buildBody(reason);
      return apiFetch(`/program-enrollment-applications/${id}/${action}`, {
        method: 'POST',
        headers: { 'Idempotency-Key': decisionKey.keyFor(`${id}:${action}:${body ?? ''}`) },
        body,
      });
    },
    onSuccess: () => decisionKey.reset(),
    onSettled: () => queryClient.invalidateQueries({ queryKey: [ROOT] }),
  });
}

export function useApproveApplication(id) {
  return useApplicationDecision(id, 'approve', () => undefined);
}

export function useRejectApplication(id) {
  return useApplicationDecision(id, 'reject', (reason) => JSON.stringify({ reason }));
}

export async function fetchApplicationDocument(applicationId, documentId) {
  const { blob } = await apiFetchBlob(`/program-enrollment-applications/${applicationId}/documents/${documentId}/file`);
  return blob;
}
