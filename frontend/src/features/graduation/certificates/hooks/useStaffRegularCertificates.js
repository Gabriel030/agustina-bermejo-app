import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const REQUESTS_KEY = ['regular-certificate-requests'];
const CERTIFICATES_KEY = ['regular-certificates'];

export function usePendingRegularRequests() {
  return useQuery({
    queryKey: [...REQUESTS_KEY, 'PENDING_STAFF'],
    queryFn: () => apiFetch('/regular-certificate-requests?outcome=PENDING_STAFF'),
  });
}

export function useIssuedRegularCertificates() {
  return useQuery({
    queryKey: CERTIFICATES_KEY,
    queryFn: () => apiFetch('/regular-certificates'),
  });
}

function useInvalidatingMutation(mutationFn) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: REQUESTS_KEY });
      queryClient.invalidateQueries({ queryKey: CERTIFICATES_KEY });
    },
  });
}

export function useIssueRegularCertificate() {
  return useInvalidatingMutation(({ kind, enrollmentId, standingEvidence }) =>
    apiFetch('/regular-certificates', {
      method: 'POST',
      body: JSON.stringify({ kind, enrollmentId, standingConfirmed: true, standingEvidence: standingEvidence ?? '' }),
    }),
  );
}

export function useRejectRegularRequest() {
  return useInvalidatingMutation(({ requestId, reason }) =>
    apiFetch(`/regular-certificate-requests/${requestId}/reject`, { method: 'POST', body: JSON.stringify({ reason }) }),
  );
}

export function useRevokeRegularCertificate() {
  return useInvalidatingMutation(({ certificateId, reason }) =>
    apiFetch(`/regular-certificates/${certificateId}/revoke`, { method: 'POST', body: JSON.stringify({ reason }) }),
  );
}
