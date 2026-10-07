import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DirectorAniosLectivos } from './DirectorAniosLectivos';

const API = 'http://localhost:8080';

describe('DirectorAniosLectivos', () => {
  it('lets the director pick a program and configure its lective years', async () => {
    server.use(
      http.get(`${API}/programs`, () => HttpResponse.json([{ id: 12, name: 'Tecnicatura en Redes' }])),
      http.get(`${API}/programs/12/year-billing-templates`, () => HttpResponse.json([{
        id: 3, programId: 12, termId: 7, recurringFeeInvoicesCount: 10, enrollmentFeeCount: 1, firstEnrollmentFeeMonth: 2,
      }])),
      http.get(`${API}/terms`, () => HttpResponse.json([{ id: 7, year: 2027 }])),
      http.get(`${API}/billing/configuration-alerts`, () => HttpResponse.json([])),
    );
    const user = userEvent.setup();
    renderWithProviders(<DirectorAniosLectivos />);

    await user.selectOptions(await screen.findByLabelText('Carrera'), '12');

    expect(await screen.findByRole('button', { name: /Configurar cobro de Año lectivo 2027/ })).toBeInTheDocument();
    expect(screen.queryByText(/Solo el director puede modificar/)).not.toBeInTheDocument();
  });
});
