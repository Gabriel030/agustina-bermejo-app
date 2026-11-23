import { useMemo } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { describeExceptionRow, indexByEnrollment } from '@/features/graduation/certificate-exceptions/lib/certificateExceptions';

const EXCEPTIONS_KEY = ['certificate-exceptions'];
const REQUESTS_KEY = ['regular-certificate-requests'];

export function useCertificateExceptions({ status, enabled = true } = {}) {
  const suffix = status ? `?status=${status}` : '';
  return useQuery({
    queryKey: [...EXCEPTIONS_KEY, 'list', status ?? 'ALL'],
    enabled,
    queryFn: () => apiFetch(`/certificate-exceptions${suffix}`),
  });
}

export function useCertificateExceptionSummary() {
  return useQuery({
    queryKey: [...EXCEPTIONS_KEY, 'summary'],
    queryFn: () => apiFetch('/certificate-exceptions/summary'),
  });
}

export function useCertificateException(id) {
  return useQuery({
    queryKey: [...EXCEPTIONS_KEY, 'detail', String(id)],
    queryFn: () => apiFetch(`/certificate-exceptions/${id}`),
  });
}

export function useAllRegularRequests() {
  return useQuery({
    queryKey: [...REQUESTS_KEY, 'ALL'],
    queryFn: () => apiFetch('/regular-certificate-requests'),
  });
}

export function useDescribedExceptions(exceptions) {
  const requests = useAllRegularRequests();
  const directory = useMemo(() => indexByEnrollment(requests.data), [requests.data]);
  const rows = useMemo(
    () => (exceptions ? exceptions.map((exception) => describeExceptionRow(exception, directory)) : undefined),
    [exceptions, directory],
  );
  return { rows, directory, requests };
}

function useExceptionMutation(mutationFn) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: EXCEPTIONS_KEY });
      queryClient.invalidateQueries({ queryKey: REQUESTS_KEY });
    },
  });
}

export function useRequestCertificateException() {
  return useExceptionMutation((body) => apiFetch('/certificate-exceptions', { method: 'POST', body: JSON.stringify(body) }));
}

export function useCreateDirectCertificateException() {
  return useExceptionMutation((body) => apiFetch('/certificate-exceptions/direct', { method: 'POST', body: JSON.stringify(body) }));
}

export function useApproveCertificateException() {
  return useExceptionMutation(({ id, reason }) =>
    apiFetch(`/certificate-exceptions/${id}/approve`, { method: 'POST', body: JSON.stringify({ reason }) }),
  );
}

export function useRejectCertificateException() {
  return useExceptionMutation(({ id, reason }) =>
    apiFetch(`/certificate-exceptions/${id}/reject`, { method: 'POST', body: JSON.stringify({ reason }) }),
  );
}

export function useWithdrawCertificateException() {
  return useExceptionMutation((id) => apiFetch(`/certificate-exceptions/${id}/withdraw`, { method: 'POST' }));
}
