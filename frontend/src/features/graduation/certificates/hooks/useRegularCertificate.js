import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiFetchBlob } from '@/api/client';

const stateKey = (enrollmentId) => ['me', 'regular-certificates', 'PROGRAM', enrollmentId];

export function useRegularCertificateState(enrollmentId) {
  return useQuery({
    queryKey: stateKey(enrollmentId),
    queryFn: () => apiFetch(`/me/regular-certificates?kind=PROGRAM&enrollmentId=${enrollmentId}`),
    enabled: !!enrollmentId,
  });
}

export function useRequestRegularCertificate(enrollmentId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch('/me/regular-certificates', { method: 'POST', body: JSON.stringify({ kind: 'PROGRAM', enrollmentId }) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: stateKey(enrollmentId) }),
  });
}

export function useCancelRegularCertificateRequest(enrollmentId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (requestId) => apiFetch(`/me/regular-certificate-requests/${requestId}`, { method: 'DELETE' }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: stateKey(enrollmentId) }),
  });
}

export function useDownloadRegularCertificatePdf(enrollmentId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (certificateId) => apiFetchBlob(`/me/regular-certificates/${certificateId}/pdf`),
    onError: () => queryClient.invalidateQueries({ queryKey: stateKey(enrollmentId) }),
  });
}
