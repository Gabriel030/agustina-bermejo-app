import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiFetchBlob } from '@/api/client';
import { CERTIFICATES_KEY } from '@/features/graduation/certificates/hooks/useStaffGraduationCertificates';

const PAGE_SIZE = 20;

export function useIssuedGraduationCertificates({ kind, status, query, page }) {
  const trimmedQuery = query.trim();
  const params = new URLSearchParams({ kind, page: String(page - 1), size: String(PAGE_SIZE) });
  if (status !== 'ALL') params.set('status', status);
  if (trimmedQuery) params.set('query', trimmedQuery);
  return useQuery({
    queryKey: [...CERTIFICATES_KEY, 'issued', kind, status, trimmedQuery, page],
    queryFn: () => apiFetch(`/graduation-certificates?${params}`),
    placeholderData: keepPreviousData,
  });
}

export function useGraduationCertificateDetail(id) {
  return useQuery({
    queryKey: [...CERTIFICATES_KEY, 'detail', id],
    queryFn: () => apiFetch(`/graduation-certificates/${id}`),
    enabled: id != null,
  });
}

export function fetchGraduationCertificatePdf(id) {
  return apiFetchBlob(`/graduation-certificates/${id}/pdf`);
}

function useCertificateOperation(operation) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }) =>
      apiFetch(`/graduation-certificates/${id}/${operation}`, { method: 'POST', body: JSON.stringify({ reason }) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: CERTIFICATES_KEY }),
  });
}

export function useRevokeGraduationCertificate() {
  return useCertificateOperation('revoke');
}

export function useReissueGraduationCertificate() {
  return useCertificateOperation('reissue');
}
