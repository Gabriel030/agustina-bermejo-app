import { describe, expect, it } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useInvitationWaveAccess } from './useInvitationWaves';

function wrapperFor(client) {
  return function Wrapper({ children }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

describe('useInvitationWaveAccess', () => {
  it('startsWithEveryCapabilityBecauseTheRouteIsAlreadyRestrictedByRole', () => {
    const { result } = renderHook(() => useInvitationWaveAccess(), { wrapper: wrapperFor(new QueryClient()) });

    expect(result.current).toMatchObject({ read: true, manage: true, send: true });
  });

  it('turnsOffOnlyTheCapabilityTheServerRefused', async () => {
    const { result } = renderHook(() => useInvitationWaveAccess(), { wrapper: wrapperFor(new QueryClient()) });

    act(() => result.current.deny('send'));

    await waitFor(() => expect(result.current).toMatchObject({ read: true, manage: true, send: false }));
  });

  it('sharesWhatWasLearnedBetweenScreens', () => {
    const client = new QueryClient();
    const first = renderHook(() => useInvitationWaveAccess(), { wrapper: wrapperFor(client) });
    act(() => first.result.current.deny('manage'));

    const second = renderHook(() => useInvitationWaveAccess(), { wrapper: wrapperFor(client) });

    expect(second.result.current.manage).toBe(false);
  });
});
