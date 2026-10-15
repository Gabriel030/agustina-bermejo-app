import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DirectorAprobaciones } from '@/features/workspace/pages/DirectorAprobaciones';

const API = 'http://localhost:8080';

function counterOf(label) {
  return screen.getAllByText(label)[0].nextElementSibling;
}

describe('DirectorAprobaciones', () => {
  it('does not show zero in the counters while the trays are still loading', async () => {
    server.use(
      http.get(`${API}/section-evaluation-configs`, async () => {
        await delay('infinite');
        return HttpResponse.json([]);
      }),
      http.get(`${API}/graduation-requests`, async () => {
        await delay('infinite');
        return HttpResponse.json([]);
      }),
      http.get(`${API}/director-approval-requests`, async () => {
        await delay('infinite');
        return HttpResponse.json([]);
      }),
    );

    renderWithProviders(<DirectorAprobaciones />);

    expect(counterOf('Egresos por aprobar')).not.toHaveTextContent('0');
    expect(counterOf('Excepciones por decidir')).not.toHaveTextContent('0');
    expect(counterOf('Config. de evaluación pendientes')).not.toHaveTextContent('0');
  });

  it('shows an error instead of "nothing pending" when a tray fails to load', async () => {
    server.use(
      http.get(`${API}/section-evaluation-configs`, () => HttpResponse.json([])),
      http.get(`${API}/graduation-requests`, () => HttpResponse.json([])),
      http.get(`${API}/director-approval-requests`, () => new HttpResponse(null, { status: 500 })),
    );

    renderWithProviders(<DirectorAprobaciones />);

    expect(await screen.findByText('No pudimos cargar esta bandeja')).toBeInTheDocument();
    expect(await screen.findByText('Ningún egreso esperando tu decisión')).toBeInTheDocument();
    expect(screen.queryByText('Ninguna excepción pendiente')).not.toBeInTheDocument();
    expect(counterOf('Excepciones por decidir')).toHaveTextContent('—');
    expect(counterOf('Egresos por aprobar')).toHaveTextContent('0');
  });
});
