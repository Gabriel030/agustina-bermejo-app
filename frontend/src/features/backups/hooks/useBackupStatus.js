import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useBackupStatus() {
  return useQuery({
    queryKey: ['backups', 'status'],
    queryFn: () => apiFetch('/backups/status'),
    retry: (count, error) => error?.status !== 403 && count < 2,
  });
}
