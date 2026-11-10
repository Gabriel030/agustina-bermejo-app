import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { DatosPersonalesTab } from './DatosPersonalesTab';

const API = 'http://localhost:8080';

describe('DatosPersonalesTab load error', () => {
  it('shows the retry state instead of saying there are no documents', async () => {
    server.use(http.get(`${API}/students/44/documents`, () => HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 })));
    renderWithProviders(
      <DatosPersonalesTab
        legajo={{ id: 44, nombre: 'Lucía', apellido: 'Fernández', legajo: '1', dni: '40111666', email: 'l@demo.test', username: '40111666', status: 'ACTIVE', onboardingStatus: 'CONFIRMADO', domicilio: {}, motivoBaja: null, detalleBaja: null }}
      />,
    );

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByText('Sin documentos cargados.')).not.toBeInTheDocument();
  });
});
