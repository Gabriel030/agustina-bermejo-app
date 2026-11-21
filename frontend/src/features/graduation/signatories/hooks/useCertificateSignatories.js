import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiFetchBlob } from '@/api/client';

export const SIGNATORIES_KEY = ['certificate-signatories'];

const LIST_SIZE = 100;

export function useCertificateSignatories() {
  return useQuery({
    queryKey: [...SIGNATORIES_KEY, 'list'],
    queryFn: () => apiFetch(`/certificate-signatories?size=${LIST_SIZE}`),
  });
}

export function useSignatorySignature(signatory, enabled) {
  return useQuery({
    queryKey: [...SIGNATORIES_KEY, 'signature', signatory.id, signatory.updatedAt],
    queryFn: async () => {
      const { blob } = await apiFetchBlob(`/certificate-signatories/${signatory.id}/signature`);
      return blob;
    },
    enabled: enabled && signatory.hasSignature,
    staleTime: Infinity,
  });
}

function useSignatoryMutation(mutationFn) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSettled: () => queryClient.invalidateQueries({ queryKey: SIGNATORIES_KEY }),
  });
}

function jsonRequest(method, body) {
  return { method, body: JSON.stringify(body) };
}

export function useCreateSignatory() {
  return useSignatoryMutation((body) => apiFetch('/certificate-signatories', jsonRequest('POST', body)));
}

export function useUpdateSignatory() {
  return useSignatoryMutation(({ id, body }) => apiFetch(`/certificate-signatories/${id}`, jsonRequest('PUT', body)));
}

export function useSetSignatoryActive() {
  return useSignatoryMutation(({ id, active }) =>
    apiFetch(`/certificate-signatories/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'POST' }),
  );
}

export function useUploadSignature() {
  return useSignatoryMutation(({ id, file }) => {
    const body = new FormData();
    body.append('file', file);
    return apiFetch(`/certificate-signatories/${id}/signature`, { method: 'PUT', body });
  });
}

export function useDeleteSignature() {
  return useSignatoryMutation((id) => apiFetch(`/certificate-signatories/${id}/signature`, { method: 'DELETE' }));
}
