import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import userEvent from '@testing-library/user-event';
import { PreceptorMesaExamen } from './PreceptorMesaExamen';

const API = 'http://localhost:8080';

globalThis.ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('PreceptorMesaExamen load error', () => {
  it('shows the retry state instead of an endless skeleton when the exam board fails to load', async () => {
    const board = {
      id: 6, subjectId: 1, courseSubjectId: 2, courseSubjectName: 'Curso', termId: 1, tribunalInstructorIds: [],
      canceledAt: null, cancelReason: null, rescheduledFrom: null, subjectName: 'Didáctica General',
      examDate: '2099-11-20T18:00:00', status: 'SCHEDULED', tribunal: [], registrationStart: null, registrationEnd: null, classroom: 'Aula 8',
    };
    server.use(
      http.get(`${API}/instructors`, () => HttpResponse.json([])),
      http.get(`${API}/exam-boards`, () => HttpResponse.json([board])),
      http.get(`${API}/exam-boards/6`, () => HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 })),
      http.get(`${API}/exam-boards/6/registrations`, () => HttpResponse.json([])),
    );
    const user = userEvent.setup();
    renderWithProviders(<PreceptorMesaExamen />);

    await user.click(await screen.findByRole('button', { name: /Buscar una mesa por materia/ }));
    await user.click(await screen.findByRole('option', { name: /Didáctica General/ }));

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cerrar acta' })).not.toBeInTheDocument();
  });
});
