import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useAnnouncementEmailSetting() {
  const query = useQuery({
    queryKey: ['institution', 'academic-settings'],
    queryFn: () => apiFetch('/institution/academic-settings'),
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

  return query.data?.announcementEmailEnabled === true;
}
