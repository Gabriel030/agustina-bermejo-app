import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DocenteConfiguracionEvaluacion } from './DocenteConfiguracionEvaluacion';

const API = 'http://localhost:8080';

describe('DocenteConfiguracionEvaluacion — load failure', () => {
  it('shows the error state and requests the configuration again on retry', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/me/section-evaluation-configs/49`, () => {
        calls += 1;
        return new HttpResponse(null, { status: 500 });
      }),
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );
    const user = userEvent.setup();
    renderWithProviders(
      <Routes>
        <Route path="/docente/mis-comisiones/:sectionId/configuracion" element={<DocenteConfiguracionEvaluacion />} />
      </Routes>,
      { route: '/docente/mis-comisiones/49/configuracion' },
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar la configuración de evaluación');
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    await waitFor(() => expect(calls).toBe(2));
  });
});
