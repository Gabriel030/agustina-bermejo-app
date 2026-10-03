import process from 'node:process';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DirectorAprobacionesExcepciones } from '@/pages/DirectorAprobacionesExcepciones';

const API = 'http://localhost:8080';
const originalTimeZone = process.env.TZ;

function exception(id, studentName, requestedAt) {
  return {
    id,
    type: 'LATE_EXAM',
    programEnrollmentId: id,
    studentName,
    programName: 'Profesorado de Educación Inicial',
    subjectId: 1,
    subjectName: 'Didáctica General',
    justification: 'x',
    status: 'REQUESTED',
    requestedAt,
    decidedAt: null,
    decisionNotes: null,
  };
}

describe('DirectorAprobacionesExcepciones', () => {
  beforeAll(() => {
    process.env.TZ = 'America/Argentina/Buenos_Aires';
  });

  afterAll(() => {
    process.env.TZ = originalTimeZone;
  });

  it('filters "Desde" by the local day, not by UTC midnight', async () => {
    server.use(
      http.get(`${API}/director-approval-requests`, ({ request }) =>
        new URL(request.url).searchParams.get('status')
          ? HttpResponse.json([])
          : HttpResponse.json([
              exception(14, 'Pedido de anoche', '2026-10-02T22:00:00'),
              exception(15, 'Pedido de hoy', '2026-10-03T09:00:00'),
            ]),
      ),
    );
    const { container } = renderWithProviders(<DirectorAprobacionesExcepciones />);
    await screen.findByText(/Pedido de anoche/);

    const [fromInput] = container.querySelectorAll('input[type="date"]');
    fireEvent.change(fromInput, { target: { value: '2026-10-03' } });

    expect(screen.getByText(/Pedido de hoy/)).toBeInTheDocument();
    expect(screen.queryByText(/Pedido de anoche/)).not.toBeInTheDocument();
  });

  it('shows an error instead of "no pending exceptions" when the list fails to load', async () => {
    server.use(http.get(`${API}/director-approval-requests`, () => new HttpResponse(null, { status: 500 })));

    renderWithProviders(<DirectorAprobacionesExcepciones />);

    expect(await screen.findByText('No pudimos cargar los trámites')).toBeInTheDocument();
    expect(screen.queryByText('Ninguna excepción pendiente')).not.toBeInTheDocument();
  });
});
