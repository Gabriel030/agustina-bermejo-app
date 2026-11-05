import { useCallback, useRef } from 'react';
import { newIdempotencyKey } from '@/api/idempotencyKey';

export const UNKNOWN_OUTCOME_CODE = 'OUTCOME_UNKNOWN';
export const IDEMPOTENCY_KEY_CONFLICT_CODE = 'IDEMPOTENCY_KEY_CONFLICT';
export const IDEMPOTENCY_KEY_INVALID_CODE = 'IDEMPOTENCY_KEY_INVALID';

export function useIdempotencyKey() {
  const attempt = useRef({ signature: null, key: null });
  return useCallback((content) => {
    const signature = JSON.stringify(content);
    if (attempt.current.signature !== signature) {
      attempt.current = { signature, key: newIdempotencyKey() };
    }
    return attempt.current.key;
  }, []);
}

export function isUnknownOutcome(error) {
  return error?.status === undefined || error.status >= 500;
}
