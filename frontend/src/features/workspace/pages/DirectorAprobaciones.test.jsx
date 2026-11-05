import { beforeEach, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DirectorAprobaciones } from '@/features/workspace/pages/DirectorAprobaciones';

const API = 'http://localhost:8080';

function counterOf(label) {
  return screen.getAllByText(label)[0].nextElementSibling;
}

const emptyBillingPage = { items: [], page: 0, size: 50, totalElements: 0 };

describe('DirectorAprobaciones', () => {
  beforeEach(() => {
    server.use(http.get(`${API}/authorization-requests`, () => HttpResponse.json(emptyBillingPage)));
  });

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

  it('links the cards, the section headers and the preview rows to their destinations', async () => {
    server.use(
      http.get(`${API}/section-evaluation-configs`, () => HttpResponse.json([])),
      http.get(`${API}/graduation-requests`, () =>
        HttpResponse.json([{ id: 5, studentName: 'Martina Acosta', programName: 'Profesorado', requestedAt: '2026-09-20T10:00:00', decidedAt: null, programEnrollmentId: 2, rejectionReason: null, status: 'ADMIN_VERIFIED', studentId: 7 }]),
      ),
      http.get(`${API}/director-approval-requests`, () =>
        HttpResponse.json([{ id: 9, type: 'LATE_EXAM', studentName: 'Benjamín Coronel', requestedAt: '2026-09-21T10:00:00', decidedAt: null, decisionNotes: null, justification: 'x', programEnrollmentId: 3, programName: 'Profesorado', status: 'REQUESTED', subjectId: 40, subjectName: 'Práctica' }]),
      ),
    );

    renderWithProviders(<DirectorAprobaciones />);

    expect(await screen.findByRole('link', { name: /Martina Acosta/ })).toHaveAttribute('href', '/director/aprobaciones/egresos/5');
    expect(screen.getByRole('link', { name: /Benjamín Coronel/ })).toHaveAttribute('href', '/director/aprobaciones/excepciones/9');
    expect(screen.getAllByRole('link', { name: /Ver el pendiente/ })).toHaveLength(2);
    expect(screen.queryByText(/lo que ya decidiste/)).not.toBeInTheDocument();
  });

  it('lets the keyboard reach the section links', async () => {
    server.use(
      http.get(`${API}/section-evaluation-configs`, () => HttpResponse.json([])),
      http.get(`${API}/graduation-requests`, () => HttpResponse.json([])),
      http.get(`${API}/director-approval-requests`, () => HttpResponse.json([])),
    );
    const user = userEvent.setup();
    renderWithProviders(<DirectorAprobaciones />);

    await screen.findAllByRole('link');
    await user.tab();

    expect(document.activeElement.tagName).toBe('A');
  });
});
