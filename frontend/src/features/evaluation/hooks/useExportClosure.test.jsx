import { describe, expect, it, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const clientMock = vi.hoisted(() => ({ apiFetchBlob: vi.fn() }));
vi.mock('@/api/client', async (importOriginal) => ({ ...(await importOriginal()), apiFetchBlob: clientMock.apiFetchBlob }));

import { useExportClosure } from './useClosureSheet';

describe('useExportClosure', () => {
  it('requestsTheExportEndpointWithTheChosenFormatAndSavesTheFile', async () => {
    clientMock.apiFetchBlob.mockResolvedValue({ blob: new Blob(['x']), filename: 'cierre-borrador.xlsx' });
    URL.createObjectURL = vi.fn(() => 'blob:x');
    URL.revokeObjectURL = vi.fn();
    const wrapper = ({ children }) => <QueryClientProvider client={new QueryClient()}>{children}</QueryClientProvider>;
    const { result } = renderHook(() => useExportClosure(31), { wrapper });

    const filename = await result.current.mutateAsync('xlsx');

    expect(filename).toBe('cierre-borrador.xlsx');
    expect(clientMock.apiFetchBlob).toHaveBeenCalledWith('/commission-closures/31/export?format=xlsx');
    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:x'));
  });
});
