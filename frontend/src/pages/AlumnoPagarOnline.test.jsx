import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { toast } from 'sonner';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AlumnoPagarOnline } from '@/pages/AlumnoPagarOnline';

const API = 'http://localhost:8080';

describe('AlumnoPagarOnline', () => {
  it('redirects to the method selection without creating a preference while the gateway is disabled', async () => {
    const infoSpy = vi.spyOn(toast, 'info').mockImplementation(() => {});
    let preferenceRequests = 0;
    server.use(
      http.get(`${API}/payment-config`, () => HttpResponse.json({ bankAlias: 'isab.pagos', mpEnabled: false })),
      http.post(`${API}/me/invoices/61/mp-preference`, () => {
        preferenceRequests += 1;
        return HttpResponse.json({ initPoint: 'https://mp.example/checkout' }, { status: 201 });
      }),
    );

    renderWithProviders(
      <Routes>
        <Route path="/alumno/pagos/:invoiceId/online" element={<AlumnoPagarOnline />} />
        <Route path="/alumno/pagos/:invoiceId/seleccion" element={<p>selection page</p>} />
      </Routes>,
      { route: '/alumno/pagos/61/online' },
    );

    expect(await screen.findByText('selection page')).toBeInTheDocument();
    await waitFor(() => expect(infoSpy).toHaveBeenCalledTimes(1));
    expect(preferenceRequests).toBe(0);
    infoSpy.mockRestore();
  });
});
