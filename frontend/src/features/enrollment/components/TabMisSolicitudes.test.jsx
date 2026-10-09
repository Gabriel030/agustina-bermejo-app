import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { TabMisSolicitudes } from '@/features/enrollment/components/TabMisSolicitudes';

const API = 'http://localhost:8080';

function mockSolicitudes(carrera, curso) {
  server.use(
    http.get(`${API}/me/program-enrollment-applications`, () => HttpResponse.json(carrera)),
    http.get(`${API}/me/course-enrollment-applications`, () => HttpResponse.json(curso)),
  );
}

describe('TabMisSolicitudes', () => {
  it('muestra un estado vacío con explicación cuando no hay solicitudes', async () => {
    mockSolicitudes([], []);
    renderWithProviders(<TabMisSolicitudes />);

    expect(await screen.findByText('Todavía no tenés solicitudes')).toBeInTheDocument();
  });

  it('lista las solicitudes en una tabla con el estado de cada una', async () => {
    mockSolicitudes(
      [{ id: 1, programName: 'Diseño Gráfico', status: 'PENDING', requestedAt: '2026-03-02T10:00:00' }],
      [{ id: 2, courseName: 'Primeros Auxilios', paymentStatus: 'PENDING_REVIEW' }],
    );
    renderWithProviders(<TabMisSolicitudes />);

    expect(await screen.findByRole('table')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Diseño Gráfico' })).toBeInTheDocument();
    expect(screen.getByText('Primeros Auxilios')).toBeInTheDocument();
    expect(screen.getAllByText('En revisión')).toHaveLength(2);
  });

  it('una solicitud aprobada con matrícula pagada e inscripción creada figura como inscripto', async () => {
    mockSolicitudes(
      [
        {
          id: 1,
          programId: 4,
          programName: 'Diseño Gráfico',
          status: 'APPROVED',
          programEnrollmentId: 15,
          admissionInvoice: { id: 77, paymentStatus: 'PAID' },
        },
      ],
      [],
    );
    renderWithProviders(<TabMisSolicitudes />);

    expect(await screen.findByText('Inscripto')).toBeInTheDocument();
    expect(screen.queryByText('Confirmando inscripción')).not.toBeInTheDocument();
  });
});
