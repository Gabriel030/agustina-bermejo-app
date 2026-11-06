import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const SETTINGS_PATH = '/institution/academic-settings';
export const INSTITUTION_SETTINGS_KEY = ['institution', 'academic-settings'];

export function useInstitutionSettings() {
  return useQuery({
    queryKey: INSTITUTION_SETTINGS_KEY,
    queryFn: () => apiFetch(SETTINGS_PATH),
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
}

export function useSaveInstitutionSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (settings) => apiFetch(SETTINGS_PATH, { method: 'PUT', body: JSON.stringify(settings) }),
    onSuccess: (saved, settings) => {
      queryClient.setQueryData(INSTITUTION_SETTINGS_KEY, saved ?? settings);
      return queryClient.invalidateQueries({ queryKey: INSTITUTION_SETTINGS_KEY });
    },
  });
}

export function useAnnouncementEmailSetting() {
  const query = useInstitutionSettings();
  return query.data?.announcementEmailEnabled === true;
}
