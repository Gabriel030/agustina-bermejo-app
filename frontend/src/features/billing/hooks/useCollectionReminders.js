import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const SETTINGS_PATH = '/billing-configuration/COLLECTION_REMINDERS/topics/COLLECTION_REMINDERS';
const SCHEDULES_PATH = '/billing-configuration/collection-reminder-schedules';

function post(path, payload) {
  return apiFetch(path, { method: 'POST', body: JSON.stringify(payload) });
}

export function schedulePath(scope) {
  return scope.targetId != null ? `${SCHEDULES_PATH}/${scope.group}/exceptions/${scope.targetId}` : `${SCHEDULES_PATH}/${scope.group}`;
}

export function useReminderPolicies() {
  return useQuery({ queryKey: ['reminder-schedules', 'policies'], queryFn: () => apiFetch(SCHEDULES_PATH) });
}

export function useReminderScheduleVersions(scope) {
  return useQuery({
    queryKey: ['reminder-schedules', 'versions', scope?.group, scope?.targetId ?? null],
    queryFn: () => apiFetch(`${schedulePath(scope)}/versions`),
    enabled: !!scope,
  });
}

export function useReminderSettingsVersions() {
  return useQuery({ queryKey: ['reminder-settings', 'versions'], queryFn: () => apiFetch(`${SETTINGS_PATH}/versions`) });
}

export function useReminderImpact() {
  return useMutation({ mutationFn: (payload) => post('/collection-reminders/impact', payload) });
}

export function useReminderValidation() {
  return useMutation({ mutationFn: ({ scope, payload }) => post(`${schedulePath(scope)}/validation`, payload) });
}

export function useReminderSettingsValidation() {
  return useMutation({ mutationFn: (payload) => post(`${SETTINGS_PATH}/validation`, payload) });
}

function useChangeMutation(mutationFn) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reminder-schedules'] });
      queryClient.invalidateQueries({ queryKey: ['reminder-settings'] });
      queryClient.invalidateQueries({ queryKey: ['authorization-requests'] });
    },
  });
}

export function useSubmitReminderChange() {
  return useChangeMutation(({ scope, payload }) => post(`${schedulePath(scope)}/changes`, payload));
}

export function useSubmitReminderRemoval() {
  return useChangeMutation(({ scope, payload }) => post(`${schedulePath(scope)}/removal`, payload));
}

export function useSubmitReminderSettings() {
  return useChangeMutation((payload) => post(`${SETTINGS_PATH}/changes`, payload));
}

export function useContactList({ page = 0, size = 100, currency, onlyNewlyListed } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (currency) params.set('currency', currency);
  if (onlyNewlyListed) params.set('onlyNewlyListed', 'true');
  return useQuery({
    queryKey: ['collection-reminders', 'contact-list', page, size, currency ?? null, !!onlyNewlyListed],
    queryFn: () => apiFetch(`/collection-reminders/contact-list?${params}`),
    placeholderData: keepPreviousData,
  });
}

export function useSimulatedEmails(page, size = 20) {
  return useQuery({
    queryKey: ['notification-deliveries', 'simulated', page, size],
    queryFn: () => apiFetch(`/notification-deliveries/simulated?page=${page}&size=${size}`),
    placeholderData: keepPreviousData,
    retry: false,
  });
}
