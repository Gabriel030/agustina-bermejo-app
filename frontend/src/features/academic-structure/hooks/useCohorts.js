import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useProgramBillingTemplates(programId) {
  return useQuery({
    queryKey: ['program-billing-templates', programId],
    queryFn: () => apiFetch(`/programs/${programId}/year-billing-templates`),
    enabled: !!programId,
  });
}

export function useUpdateYearBillingConfiguration() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ templateId, ...body }) =>
      apiFetch(`/year-billing-templates/${templateId}/configuration`, { method: 'PATCH', body: JSON.stringify(body) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['program-billing-templates'] });
      queryClient.invalidateQueries({ queryKey: ['billing-configuration-alerts'] });
    },
  });
}

export function useBillingConfigurationAlerts() {
  return useQuery({
    queryKey: ['billing-configuration-alerts'],
    queryFn: () => apiFetch('/billing/configuration-alerts'),
  });
}

export function useProgramCohorts(programId) {
  return useQuery({
    queryKey: ['program-cohorts', programId],
    queryFn: () => apiFetch(`/programs/${programId}/cohorts`),
    enabled: !!programId,
  });
}

export function useCreateCohort() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ templateId, ...body }) =>
      apiFetch(`/year-billing-templates/${templateId}/cohorts`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['program-cohorts'] });
      queryClient.invalidateQueries({ queryKey: ['program-billing-templates'] });
    },
  });
}
