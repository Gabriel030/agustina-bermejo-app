import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { LlamadosDisponibles } from '@/components/alumno/TabMesasDeExamen';

const API = 'http://localhost:8080';

describe('LlamadosDisponibles', () => {
  it('explica que las inasistencias bloquean el final en vez de un error genérico', async () => {
    server.use(
      http.get(`${API}/me/exam-boards`, () => HttpResponse.json([{ id: 1, examDate: '2026-12-01T09:00:00', classroom: null }])),
      http.post(`${API}/me/exam-board-registrations`, () =>
        HttpResponse.json({ code: 'STUDENT_FREE_BY_ABSENCES' }, { status: 409 }),
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
