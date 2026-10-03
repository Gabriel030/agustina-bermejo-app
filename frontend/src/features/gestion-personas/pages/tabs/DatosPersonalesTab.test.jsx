import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DatosPersonalesTab } from '@/features/gestion-personas/pages/tabs/DatosPersonalesTab';

const API = 'http://localhost:8080';

describe('DatosPersonalesTab', () => {
  it('shows the sex translated instead of its code', async () => {
    server.use(http.get(`${API}/students/53/documents`, () => HttpResponse.json([])));
    renderWithProviders(
      <DatosPersonalesTab
        legajo={{ id: 53, nombre: 'Ana', apellido: 'Pérez', sexo: 'F', status: 'ACTIVE' }}
        puedeAccionar={false}
        puedeDarDeBaja={false}
      />,
    );

    expect(await screen.findByDisplayValue('Femenino')).toBeInTheDocument();
    expect(screen.queryByDisplayValue('F')).not.toBeInTheDocument();
  });
});
