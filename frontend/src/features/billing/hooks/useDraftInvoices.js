import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const draftsKey = (monthlyRunId) => ['monthly-runs', 'drafts', monthlyRunId];

export function useDraftInvoiceGroups(monthlyRunId) {
  return useQuery({
    queryKey: draftsKey(monthlyRunId),
    queryFn: () => apiFetch(`/monthly-invoice-runs/${monthlyRunId}/draft-invoices`),
    enabled: !!monthlyRunId,
  });
}

export function useApproveDraftInvoices(monthlyRunId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ invoiceIds, idempotencyKey }) =>
      apiFetch(`/monthly-invoice-runs/${monthlyRunId}/draft-invoices/approvals`, {
        method: 'POST',
        headers: { 'Idempotency-Key': idempotencyKey },
        body: JSON.stringify({ invoiceIds }),
      }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['monthly-runs'] }),
  });
}

export function useDiscardDraftInvoices(monthlyRunId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ invoiceIds, reason }) =>
      apiFetch(`/monthly-invoice-runs/${monthlyRunId}/draft-invoices/discards`, {
        method: 'POST',
        body: JSON.stringify({ invoiceIds, reason }),
      }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['monthly-runs'] }),
  });
}
