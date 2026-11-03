import { useRef } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { FIRST_DIRECTOR_STATUS_KEY } from '@/features/platform/hooks/useFirstDirectorStatus';

export function useProvisionFirstDirector() {
  const queryClient = useQueryClient();
  const attempt = useRef({ signature: null, idempotencyKey: null });

  return useMutation({
    mutationFn: (request) => {
      const signature = JSON.stringify(request);
      if (attempt.current.signature !== signature) {
        attempt.current = { signature, idempotencyKey: crypto.randomUUID() };
      }
      return apiFetch('/platform/provisioning/first-director', {
        method: 'POST',
        headers: { 'Idempotency-Key': attempt.current.idempotencyKey },
        body: JSON.stringify(request),
      });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: FIRST_DIRECTOR_STATUS_KEY }),
  });
}
