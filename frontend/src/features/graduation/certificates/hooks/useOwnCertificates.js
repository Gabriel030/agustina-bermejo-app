import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiFetchBlob } from '@/api/client';

const listKey = ['me', 'graduation-certificates'];

export function useOwnCertificates() {
  return useQuery({
    queryKey: listKey,
    queryFn: () => apiFetch('/me/certificates'),
  });
}

export function useOwnCertificatePdf() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (certificateId) => apiFetchBlob(`/me/certificates/${certificateId}/pdf`),
    onError: () => queryClient.invalidateQueries({ queryKey: listKey }),
  });
}
