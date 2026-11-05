import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { INBOX_PAGE_SIZE } from '../authorization/authorizationRequests';

function buildListPath({ statuses, sort, descending, page, size }) {
  const params = new URLSearchParams();
  (statuses ?? []).forEach((status) => params.append('status', status));
  if (sort) params.set('sort', sort);
  if (descending) params.set('descending', 'true');
  params.set('page', String(page ?? 0));
  params.set('size', String(size ?? INBOX_PAGE_SIZE));
  return `/authorization-requests?${params.toString()}`;
}

export function useAuthorizationRequests(search, { enabled = true } = {}) {
  return useQuery({
    queryKey: ['authorization-requests', 'list', search],
    queryFn: () => apiFetch(buildListPath(search)),
    enabled,
    placeholderData: keepPreviousData,
  });
}

export function useAuthorizationRequest(id) {
  return useQuery({
    queryKey: ['authorization-requests', 'detail', id],
    queryFn: () => apiFetch(`/authorization-requests/${id}`),
    enabled: !!id,
    retry: false,
  });
}

function useDecisionMutation(path) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, decisionReason }) =>
      apiFetch(`/authorization-requests/${id}/${path}`, {
        method: 'POST',
        body: decisionReason ? JSON.stringify({ decisionReason }) : undefined,
      }),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['authorization-requests'] });
      queryClient.invalidateQueries({ queryKey: ['students'] });
    },
  });
}

export function useApproveAuthorizationRequest() {
  return useDecisionMutation('approve');
}

export function useRejectAuthorizationRequest() {
  return useDecisionMutation('reject');
}

export function useWithdrawAuthorizationRequest() {
  return useDecisionMutation('withdraw');
}
