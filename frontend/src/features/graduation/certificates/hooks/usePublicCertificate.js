import { useQuery } from '@tanstack/react-query';
import { fetchPublicCertificate } from '@/features/graduation/certificates/lib/fetchPublicCertificate';
import { isWellFormedCertificateCode } from '@/features/graduation/certificates/lib/certificateCode';

export function usePublicCertificate(normalizedCode) {
  return useQuery({
    queryKey: ['public-certificate', normalizedCode],
    queryFn: ({ signal }) => fetchPublicCertificate(normalizedCode, { signal }),
    enabled: isWellFormedCertificateCode(normalizedCode),
    gcTime: 0,
    staleTime: 0,
    retry: false,
    networkMode: 'always',
    refetchOnMount: 'always',
    refetchOnReconnect: false,
    refetchOnWindowFocus: false,
  });
}
