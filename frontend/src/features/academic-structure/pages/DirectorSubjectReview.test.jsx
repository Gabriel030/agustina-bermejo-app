import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DirectorSubjectReview } from '@/features/academic-structure/pages/DirectorSubjectReview';

const API = 'http://localhost:8080';

const PENDING = [
  { id: 7, code: 'S113', name: 'Farmacología', status: 'PENDING_APPROVAL', gradeMode: 'NUMERIC', description: null },
];

function mockPendingSubjects() {
  server.use(
    http.get(`${API}/subjects`, () =>
      HttpResponse.json([{ id: 7, code: 'S113', name: 'Anatomía y Fisiología Humana', gradeMode: 'NUMERIC' }]),
    ),
  );
}

describe('DirectorSubjectReview', () => {
  it('asks for a mandatory reason before rejecting and sends it to the backend', async () => {
    let rejectBody = null;
    server.use(
      http.get(`${API}/subjects`, () => HttpResponse.json(PENDING)),
      http.post(`${API}/subjects/7/reject`, async ({ request }) => {
        rejectBody = await request.json();
        return HttpResponse.json({ ...PENDING[0], status: 'REJECTED', rejectionReason: rejectBody.reason });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<DirectorSubjectReview />);

    await user.click(await screen.findByRole('button', { name: /Rechazar/ }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toBeInTheDocument();
    expect(rejectBody).toBeNull();

    await user.click(screen.getByRole('button', { name: 'Rechazar' }));
    expect(await screen.findByText('El motivo del rechazo es obligatorio.')).toBeInTheDocument();
    expect(rejectBody).toBeNull();

    await user.type(screen.getByPlaceholderText('Motivo del rechazo'), 'Sin bibliografía');
    await user.click(screen.getByRole('button', { name: 'Rechazar' }));

    await waitFor(() => expect(rejectBody).toEqual({ reason: 'Sin bibliografía' }));
  });

  it('shows the page title', async () => {
    mockPendingSubjects();
    renderWithProviders(<DirectorSubjectReview />);

    expect(await screen.findByRole('heading', { name: 'Banco de materias' })).toBeInTheDocument();
  });

  it('stacks the subject name above the actions on narrow screens so the name is never squeezed out', async () => {
    mockPendingSubjects();
    renderWithProviders(<DirectorSubjectReview />);

    const name = await screen.findByText('Anatomía y Fisiología Humana');
    const row = name.closest('[data-testid="subject-review-row"]');

    expect(row).not.toBeNull();
    expect(row).toHaveClass('flex-col');
    expect(row).toHaveClass('sm:flex-row');
    expect(screen.getByRole('button', { name: /Aprobar/ })).toBeInTheDocument();
  });
});
