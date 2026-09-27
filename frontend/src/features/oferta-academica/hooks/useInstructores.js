import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useInstructores() {
  return useQuery({
    queryKey: ['instructores'],
    queryFn: () => apiFetch('/instructors'),
  });
}
