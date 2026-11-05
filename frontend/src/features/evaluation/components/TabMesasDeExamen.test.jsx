import { describe, expect, it } from 'vitest';
import { errorBody } from '@/test/fixtures/errors';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { TabMesasDeExamen, LlamadosDisponibles } from '@/features/evaluation/components/TabMesasDeExamen';

const API = 'http://localhost:8080';

describe('LlamadosDisponibles', () => {
  it('explica que las inasistencias bloquean el final en vez de un error genérico', async () => {
    server.use(
      http.get(`${API}/me/exam-boards`, () => HttpResponse.json([{ id: 1, examDate: '2026-12-01T09:00:00', classroom: null }])),
      http.post(`${API}/me/exam-board-registrations`, () =>
        HttpResponse.json(errorBody('STUDENT_FREE_BY_ABSENCES'), { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    renderWithProviders(<LlamadosDisponibles subjectId={3} programSectionEnrollmentId={9} misInscripciones={[]} />);

    await user.click(await screen.findByRole('button', { name: 'Inscribirme a este llamado' }));

    expect(
      await screen.findByText('Superaste el máximo de inasistencias: justificalas para poder inscribirte al final.'),
    ).toBeInTheDocument();
  });

  it('ante otro error muestra el mensaje genérico', async () => {
    server.use(
      http.get(`${API}/me/exam-boards`, () => HttpResponse.json([{ id: 1, examDate: '2026-12-01T09:00:00', classroom: null }])),
      http.post(`${API}/me/exam-board-registrations`, () => new HttpResponse(null, { status: 500 })),
    );
    const user = userEvent.setup();
    renderWithProviders(<LlamadosDisponibles subjectId={3} programSectionEnrollmentId={9} misInscripciones={[]} />);

    await user.click(await screen.findByRole('button', { name: 'Inscribirme a este llamado' }));

    expect(await screen.findByText('No pudimos inscribirte, probá de nuevo.')).toBeInTheDocument();
  });
});

describe('TabMesasDeExamen', () => {
  it('distingue una mesa cancelada de una inscripción dada de baja', async () => {
    const mesa = {
      subjectName: 'Didáctica',
      examDate: '2026-12-01T09:00:00',
      classroom: 'Aula 1',
      subjectId: 3,
      courseSubjectId: 4,
      courseSubjectName: 'Didáctica',
      termId: 1,
      registrationStart: '2026-11-01T09:00:00',
      registrationEnd: '2026-11-20T09:00:00',
      tribunal: [],
      tribunalInstructorIds: [],
      canceledAt: null,
      cancelReason: null,
      rescheduledFrom: null,
    };
    server.use(
      http.get(`${API}/me/program-section-enrollments`, () => HttpResponse.json([])),
      http.get(`${API}/me/exam-board-registrations`, () =>
        HttpResponse.json([
          { id: 1, enrollmentId: 9, status: 'WITHDRAWN', score: 0, examBoard: { ...mesa, id: 10, status: 'CANCELED', cancelReason: 'Paro docente' } },
          { id: 2, enrollmentId: 9, status: 'WITHDRAWN', score: 0, examBoard: { ...mesa, id: 11, status: 'SCHEDULED' } },
        ]),
      ),
    );
    const user = userEvent.setup();
    renderWithProviders(<TabMesasDeExamen />);

    await user.click(await screen.findByRole('tab', { name: 'Mis inscripciones' }));

    expect(await screen.findByText('Mesa cancelada')).toBeInTheDocument();
    expect(screen.getByText('Motivo: Paro docente')).toBeInTheDocument();
    expect(screen.getByText('De baja')).toBeInTheDocument();
  });
});
