import { describe, expect, it, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { server } from '@/test/mocks/server';
import { mockClosureExport } from '@/test/fixtures/closureList';
import { useExportClosure } from './useClosureSheet';

describe('useExportClosure', () => {
  it('requestsTheExportEndpointWithTheChosenFormatAndSavesTheFileWithTheServerName', async () => {
    mockClosureExport(server, 31, 'cierre-teorias-socioculturales-comision-a-manana-borrador.xlsx');
    URL.createObjectURL = vi.fn(() => 'blob:x');
    URL.revokeObjectURL = vi.fn();
    const wrapper = ({ children }) => <QueryClientProvider client={new QueryClient()}>{children}</QueryClientProvider>;
    const { result } = renderHook(() => useExportClosure(31), { wrapper });

    const filename = await result.current.mutateAsync('xlsx');

    expect(filename).toBe('cierre-teorias-socioculturales-comision-a-manana-borrador.xlsx');
    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:x'));
  });

  it('sendsTheChosenFormatInTheQuery', async () => {
    let requestedFormat;
    const { http, HttpResponse } = await import('msw');
    server.use(
      http.get('http://localhost:8080/commission-closures/31/export', ({ request }) => {
        requestedFormat = new URL(request.url).searchParams.get('format');
        return new HttpResponse(new Uint8Array([1]), { headers: { 'Content-Disposition': 'attachment; filename="acta.pdf"' } });
      }),
    );
    URL.createObjectURL = vi.fn(() => 'blob:y');
    URL.revokeObjectURL = vi.fn();
    const wrapper = ({ children }) => <QueryClientProvider client={new QueryClient()}>{children}</QueryClientProvider>;
    const { result } = renderHook(() => useExportClosure(31), { wrapper });

    await result.current.mutateAsync('pdf');

    expect(requestedFormat).toBe('pdf');
  });
});
