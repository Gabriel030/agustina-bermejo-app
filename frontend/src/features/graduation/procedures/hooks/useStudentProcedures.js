import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiFetchBlob } from '@/api/client';

const approvedKey = ['me', 'procedures', 'approved'];

export function useProcedureEligibility(type, { enrollmentId, registrationId } = {}) {
  const params = new URLSearchParams();
  if (enrollmentId) params.set('enrollmentId', enrollmentId);
  if (registrationId) params.set('registrationId', registrationId);
  const query = params.toString();
  return useQuery({
    queryKey: ['me', 'procedures', type, 'eligibility', enrollmentId ?? null, registrationId ?? null],
    queryFn: () => apiFetch(`/me/procedures/${type}/eligibility${query ? `?${query}` : ''}`),
    enabled: !!enrollmentId,
  });
}

export function useApprovedProcedures() {
  return useQuery({ queryKey: approvedKey, queryFn: () => apiFetch('/me/procedures/approved') });
}

export function useApprovedProcedurePdf() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ documentType, id }) => apiFetchBlob(`/me/procedures/approved/${documentType}/${id}/pdf`),
    onSettled: () => queryClient.invalidateQueries({ queryKey: approvedKey }),
  });
}

export function useGenerateProcedureDocument() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ type, body }) => {
      const created =
        type === 'APPROVED_SUBJECTS_CERTIFICATE'
          ? await apiFetch('/me/approved-subjects-certificates', {
              method: 'POST',
              body: JSON.stringify({ enrollmentId: body.enrollmentId }),
            })
          : await apiFetch(`/me/documents/${type}`, { method: 'POST', body: JSON.stringify(body) });
      const document = {
        documentType: type,
        id: created.id,
      };
      const file = await apiFetchBlob(`/me/procedures/approved/${document.documentType}/${document.id}/pdf`);
      return { document, file };
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['me', 'procedures'] }),
  });
}
