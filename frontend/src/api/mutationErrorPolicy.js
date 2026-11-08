import { MutationCache, MutationObserver } from '@tanstack/react-query';
import { toast } from 'sonner';
import { ApiError } from '@/api/apiError';
import { NetworkError } from '@/api/client';
import { describeError, ERROR_MESSAGES } from '@/api/describeError';

const GENERIC_MESSAGE = 'No pudimos completar la acción. Probá de nuevo.';

const callerHandling = new WeakMap();
let patched = false;
let collecting = null;

export function describeMutationFailure(error) {
  const { kind, message, requestId } = describeError(error, { fallback: GENERIC_MESSAGE });
  const translated = Boolean(error?.code && ERROR_MESSAGES[error.code]);
  const known = translated || kind === 'network' || kind === 'circuit' || kind === 'rate-limit';
  const base = known ? message : GENERIC_MESSAGE;
  return requestId && !translated ? `${base} Código de seguimiento: ${requestId}` : base;
}

function isHandledByCaller(mutation) {
  if (mutation.options.onError || mutation.meta?.handlesOwnError === true) return true;
  const handling = callerHandling.get(mutation);
  if (!handling) return false;
  return handling.onError || !handling.discardedByMutate;
}

export function reportMutationError(error, mutation) {
  if (isHandledByCaller(mutation)) return;
  toast.error(describeMutationFailure(error));
}

function trackObserverCalls() {
  if (patched) return;
  patched = true;
  const original = MutationObserver.prototype.mutate;
  MutationObserver.prototype.mutate = function trackedMutate(variables, options) {
    const added = [];
    collecting = added;
    let promise;
    try {
      promise = original.call(this, variables, options);
    } finally {
      collecting = null;
    }
    const handling = { onError: typeof options?.onError === 'function', discardedByMutate: false };
    added.forEach((mutation) => callerHandling.set(mutation, handling));
    const originalCatch = promise.catch.bind(promise);
    promise.catch = (...args) => {
      handling.discardedByMutate = true;
      return originalCatch(...args);
    };
    return promise;
  };
}

export function createMutationCache() {
  trackObserverCalls();
  const cache = new MutationCache({
    onError: (error, _variables, _context, mutation) => reportMutationError(error, mutation),
  });
  cache.subscribe((event) => {
    if (event.type === 'observerAdded' && collecting) collecting.push(event.mutation);
  });
  return cache;
}

let listening = false;

export function reportUnhandledMutationRejections() {
  if (listening || typeof window === 'undefined') return;
  listening = true;
  window.addEventListener('unhandledrejection', (event) => {
    const error = event.reason;
    if (!(error instanceof ApiError) && !(error instanceof NetworkError)) return;
    toast.error(describeMutationFailure(error));
  });
}
