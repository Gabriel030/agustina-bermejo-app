import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function usePersonal({ role, status, search } = {}) {
  const params = new URLSearchParams();
  if (role) params.set('role', role);
  if (status) params.set('status', status);
  if (search) params.set('search', search);
  const qs = params.toString();

  return useQuery({
    queryKey: ['personal', { role, status, search }],
    queryFn: () => apiFetch(`/staff${qs ? `?${qs}` : ''}`),
  });
}
