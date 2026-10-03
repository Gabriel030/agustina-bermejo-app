import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DirectorSubjectReview } from '@/features/gestion-academica/pages/DirectorSubjectReview';

const API = 'http://localhost:8080';

function mockPendingSubjects() {
  server.use(
    http.get(`${API}/subjects`, () =>
      HttpResponse.json([{ id: 7, code: 'S113', name: 'Anatomía y Fisiología Humana', gradeMode: 'NUMERIC' }]),
    ),
  );
}

describe('DirectorSubjectReview', () => {
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
