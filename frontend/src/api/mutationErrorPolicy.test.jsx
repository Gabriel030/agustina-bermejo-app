import { beforeEach, describe, expect, it, vi } from 'vitest';
import { QueryClientProvider, useMutation } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { apiFetch, NetworkError } from '@/api/client';
import { ApiError } from '@/api/apiError';
import { createQueryClient } from './queryClient';
import { describeMutationFailure } from './mutationErrorPolicy';

const { toastMock } = vi.hoisted(() => ({ toastMock: { error: vi.fn(), success: vi.fn() } }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

const API = 'http://localhost:8080';

function renderMutation(options) {
  const client = createQueryClient();
  const wrapper = ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  return renderHook(
    () => useMutation({ mutationFn: () => apiFetch('/students/9/reset-password', { method: 'POST' }), ...options }),
    { wrapper },
  );
}

function failWith(status, body) {
  server.use(http.post(`${API}/students/9/reset-password`, () => HttpResponse.json({ retryable: false, ...body }, { status })));
}

beforeEach(() => toastMock.error.mockClear());

describe('global mutation error policy', () => {
  it('showsTheTranslatedMessageWhenMutateFailsWithAKnownCode409', async () => {
    failWith(409, { code: 'STUDENT_HAS_ACTIVE_ENROLLMENTS' });
    const { result } = renderMutation();
    act(() => result.current.mutate());
    await waitFor(() => expect(toastMock.error).toHaveBeenCalledTimes(1));
    expect(toastMock.error).toHaveBeenCalledWith('El alumno tiene inscripciones activas. Cancelalas antes de darlo de baja.');
  });

  it('showsAGenericMessageWithTheRequestIdWhenTheCodeHasNoTranslation500', async () => {
    failWith(500, { code: 'SOMETHING_NEW', requestId: 'req-123' });
    const { result } = renderMutation();
    act(() => result.current.mutate());
    await waitFor(() => expect(toastMock.error).toHaveBeenCalledTimes(1));
    expect(toastMock.error).toHaveBeenCalledWith('No pudimos completar la acción. Probá de nuevo. Código de seguimiento: req-123');
  });

  it('showsAToastWhenTheRequestNeverReachesTheServer', async () => {
    const { result } = renderMutation({
      mutationFn: () => {
        throw new NetworkError();
      },
    });
    act(() => result.current.mutate());
    await waitFor(() => expect(toastMock.error).toHaveBeenCalledTimes(1));
    expect(toastMock.error).toHaveBeenCalledWith(new NetworkError().message);
  });

  it('showsNoGenericToastWhenTheMutationDefinesItsOwnOnError', async () => {
    failWith(409, { code: 'ILLEGAL_STATE' });
    const onError = vi.fn();
    const { result } = renderMutation({ onError });
    act(() => result.current.mutate());
    await waitFor(() => expect(onError).toHaveBeenCalledTimes(1));
    expect(toastMock.error).not.toHaveBeenCalled();
  });

  it('showsNoGenericToastWhenTheCallPassesItsOwnOnError', async () => {
    failWith(409, { code: 'ILLEGAL_STATE' });
    const onError = vi.fn();
    const { result } = renderMutation();
    act(() => result.current.mutate(undefined, { onError }));
    await waitFor(() => expect(onError).toHaveBeenCalledTimes(1));
    expect(toastMock.error).not.toHaveBeenCalled();
  });

  it('showsNoGenericToastWhenTheMutationDeclaresHandlesOwnError', async () => {
    failWith(409, { code: 'ILLEGAL_STATE' });
    const { result } = renderMutation({ meta: { handlesOwnError: true } });
    act(() => result.current.mutate());
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(toastMock.error).not.toHaveBeenCalled();
  });

  it('showsNoGenericToastWhenTheCallerAwaitsMutateAsyncAndCatches', async () => {
    failWith(409, { code: 'ILLEGAL_STATE' });
    const { result } = renderMutation();
    let caught = null;
    await act(async () => {
      try {
        await result.current.mutateAsync();
      } catch (error) {
        caught = error;
      }
    });
    expect(caught?.code).toBe('ILLEGAL_STATE');
    expect(toastMock.error).not.toHaveBeenCalled();
  });

  it('showsAToastWhenAnApiRejectionReachesTheWindowUnhandled', async () => {
    createQueryClient();
    const event = new Event('unhandledrejection');
    event.reason = new ApiError('x', { status: 409, code: 'ILLEGAL_STATE' });
    window.dispatchEvent(event);
    expect(toastMock.error).toHaveBeenCalledWith('La operación no es posible en el estado actual.');
  });

  it('ignoresUnhandledRejectionsThatAreNotApiErrors', () => {
    createQueryClient();
    const event = new Event('unhandledrejection');
    event.reason = new Error('boom');
    window.dispatchEvent(event);
    expect(toastMock.error).not.toHaveBeenCalled();
  });

  it('describesAnUntranslatedCodeWithoutRequestIdUsingOnlyTheGenericMessage', () => {
    expect(describeMutationFailure({ status: 500, code: 'X', message: 'raw' })).toBe('No pudimos completar la acción. Probá de nuevo.');
  });
});
