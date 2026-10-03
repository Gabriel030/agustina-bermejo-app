import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AdministrativoSubjects } from './AdministrativoSubjects';

const API = 'http://localhost:8080';

describe('AdministrativoSubjects', () => {
  it('lists rejected subjects in their own tab so they can be fixed and resubmitted', async () => {
    server.use(
      http.get(`${API}/subjects`, ({ request }) => {
        const status = new URL(request.url).searchParams.get('status');
        if (status === 'REJECTED') {
          return HttpResponse.json([
            { id: 124, code: 'S119', name: 'Gestión de Datos Clínicos', status: 'REJECTED', rejectionReason: 'Falta bibliografía' },
          ]);
        }
        return HttpResponse.json([]);
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<AdministrativoSubjects />, { route: '/administrativo/gestion-academica' });

    await user.click(await screen.findByRole('tab', { name: 'Rechazadas' }));

    expect(await screen.findByText('Gestión de Datos Clínicos')).toBeInTheDocument();
    expect(screen.getByText('Rechazada')).toBeInTheDocument();
  });
});
