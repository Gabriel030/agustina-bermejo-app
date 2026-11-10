import { useRef } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { newIdempotencyKey } from '@/api/idempotencyKey';

const PENDING_STATUS = 'PENDING_APPROVAL';

export function usePendingOfferCycles() {
  return useQuery({
    queryKey: ['ciclos', { status: PENDING_STATUS }],
    queryFn: () => apiFetch(`/academic-offering-cycles?status=${PENDING_STATUS}`),
  });
}

export function useCycleConflicts(cycleId) {
  return useQuery({
    queryKey: ['ciclo-conflicts', cycleId],
    queryFn: () => apiFetch(`/academic-offering-cycles/${cycleId}/conflicts`),
    enabled: !!cycleId,
  });
}

function useDecisionKey() {
  const attempt = useRef({ signature: null, key: null });
  return (signature) => {
    if (attempt.current.signature !== signature) {
      attempt.current = { signature, key: newIdempotencyKey() };
    }
    return attempt.current.key;
  };
}

function useCycleDecision(cycleId, action, buildBody) {
  const queryClient = useQueryClient();
  const keyFor = useDecisionKey();
  return useMutation({
    mutationFn: (reason) => {
      const body = buildBody(reason);
      return apiFetch(`/academic-offering-cycles/${cycleId}/${action}`, {
        method: 'POST',
        headers: { 'Idempotency-Key': keyFor(`${action}:${body ?? ''}`) },
        body,
      });
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['ciclos'] });
      queryClient.invalidateQueries({ queryKey: ['ciclo'] });
    },
  });
}

export function useApproveCycle(cycleId) {
  return useCycleDecision(cycleId, 'approve', () => undefined);
}

export function useRejectCycle(cycleId) {
  return useCycleDecision(cycleId, 'reject', (reason) => JSON.stringify({ reason }));
}
