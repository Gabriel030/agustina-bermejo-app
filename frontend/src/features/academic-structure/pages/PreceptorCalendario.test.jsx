import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PreceptorCalendario } from './PreceptorCalendario';

const API = 'http://localhost:8080';

function mockTermsAndAvailability() {
  const requestedTerms = [];
  server.use(
    http.get(`${API}/terms`, () =>
      HttpResponse.json([
        { id: 6, description: 'Ciclo lectivo 2026' },
        { id: 7, description: 'Ciclo lectivo 2027' },
      ]),
    ),
    http.get(`${API}/terms/:termId/classrooms/availability`, ({ params }) => {
      requestedTerms.push(params.termId);
      return HttpResponse.json([
        { id: 1, name: 'Laboratorio 2', capacity: 20, available: false },
        { id: 2, name: 'Aula 1', capacity: 30, available: true },
      ]);
    }),
  );
  return requestedTerms;
}

function renderCalendar(route) {
  return renderWithProviders(
    <Routes>
      <Route path="/preceptor/oferta-academica/calendario" element={<PreceptorCalendario />} />
    </Routes>,
    { route },
  );
}

describe('PreceptorCalendario', () => {
  it('loads the availability of the period given in the URL', async () => {
    const requestedTerms = mockTermsAndAvailability();
    renderCalendar('/preceptor/oferta-academica/calendario?termId=7');

    expect(await screen.findByText('Laboratorio 2')).toBeInTheDocument();
    expect(screen.getByText(/20 lugares · Ocupada/)).toBeInTheDocument();
    expect(screen.getByText(/30 lugares · Libre/)).toBeInTheDocument();
    expect(requestedTerms).toEqual(['7']);
  });

  it('lets the user choose a period when none is given', async () => {
    const requestedTerms = mockTermsAndAvailability();
    const user = userEvent.setup();
    renderCalendar('/preceptor/oferta-academica/calendario');

    expect(screen.getByText('Seleccioná un período para ver disponibilidad.')).toBeInTheDocument();

    const periodSelect = await screen.findByLabelText('Período');
    await screen.findByRole('option', { name: 'Ciclo lectivo 2026' });
    await user.selectOptions(periodSelect, '6');

    expect(await screen.findByText('Laboratorio 2')).toBeInTheDocument();
    expect(requestedTerms).toEqual(['6']);
  });
});
