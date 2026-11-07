import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { NOTIFICATIONS_KEY } from './paymentMethods';

export const INSTITUTE_SETTINGS_KEY = ['institute-settings'];
export const INSTITUTE_SETTINGS_AUDIT_KEY = ['institute-settings', 'audit'];
export const AUDIT_FETCH_SIZE = 100;

export function useInstituteSettings() {
  return useQuery({ queryKey: INSTITUTE_SETTINGS_KEY, queryFn: () => apiFetch('/institute-settings'), retry: false });
}

export function useInstituteSettingsAudit({ enabled }) {
  return useQuery({
    queryKey: INSTITUTE_SETTINGS_AUDIT_KEY,
    queryFn: () => apiFetch(`/institute-settings/audit?page=0&size=${AUDIT_FETCH_SIZE}`),
    enabled,
    retry: false,
    placeholderData: keepPreviousData,
  });
}

export function useChangeInstituteSetting() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ key, version, request }) => {
      const path = key === NOTIFICATIONS_KEY ? '/institute-settings/billing-notifications' : `/institute-settings/payment-methods/${key}`;
      return apiFetch(path, { method: 'PUT', headers: { 'If-Match': String(version) }, body: JSON.stringify(request) });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: INSTITUTE_SETTINGS_KEY }),
  });
}
