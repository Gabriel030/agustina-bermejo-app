import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installFixedClock } from '@/test/fixedClock';
import { createDeferred } from '@/test/deferred';
import { errorBody } from '@/test/fixtures/errors';
import { DirectorGradeGrid } from './DirectorGradeGrid';

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

const API = 'http://localhost:8080';

function buildGrid(correctionDeadline = '2026-12-18') {
  return {
    sectionId: 7,
    correctionDeadline,
    evaluationSchemeName: 'Cuatrimestral',
    minRegularGrade: 6,
    minApprovalGrade: 7,
    promotionMinGrade: 8,
    columns: [
      { type: 'EXAM', sequenceNumber: 1, date: '2026-09-15', published: true, publishedAt: '2026-10-01T12:00:00' },
      { type: 'EXAM', sequenceNumber: 2, date: '2026-10-27', published: false, publishedAt: null },
    ],
    students: [
      {
        enrollmentId: 11,
        lastname: 'Benítez',
        name: 'Lucía',
        dni: '42007315',
        condition: 'REGULAR',
        finalEnabled: true,
        grades: [
          { gradeItemId: 1, type: 'EXAM', sequenceNumber: 1, score: 7, date: '2026-09-16T10:00:00', comments: null, published: true, corrected: true },
          { gradeItemId: 2, type: 'EXAM', sequenceNumber: 2, score: 8, date: '2026-10-28T10:00:00', comments: null, published: false, corrected: false },
        ],
      },
    ],
  };
}

function useGrid(body) {
  server.use(http.get(`${API}/staff/program-sections/7/grade-grid`, () => HttpResponse.json(body)));
}

async function openCorrectionForm(user) {
  await user.click(await screen.findByRole('button', { name: /Parcial 1 de Benítez, Lucía: 7, corregida/ }));
  await user.click(await screen.findByRole('button', { name: 'Corregir Parcial 1' }));
}

describe('DirectorGradeGrid', () => {
  installFixedClock();

  it('marks corrected and unpublished cells and only offers Corregir on published grades', async () => {
    useGrid(buildGrid());
    const user = userEvent.setup();
    renderWithProviders(<DirectorGradeGrid sectionId="7" kind="PROGRAM" />);

    expect(await screen.findByRole('button', { name: /Parcial 1 de Benítez, Lucía: 7, corregida/ })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Parcial 2 de Benítez, Lucía: 8, sin publicar/ }));

    expect(await screen.findByRole('button', { name: 'Corregir Parcial 1' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Corregir Parcial 2' })).toBeDisabled();
  });

  it('shows the deadline banner with the date and disables Corregir when the correction period ended', async () => {
    useGrid(buildGrid('2026-10-30'));
    const user = userEvent.setup();
    renderWithProviders(<DirectorGradeGrid sectionId="7" kind="PROGRAM" />);

    expect(await screen.findByRole('alert')).toHaveTextContent('El plazo de corrección terminó el 30/10/2026.');
    await user.click(screen.getByRole('button', { name: /Parcial 1 de Benítez, Lucía/ }));

    expect(await screen.findByRole('button', { name: 'Corregir Parcial 1' })).toBeDisabled();
  });

  it('corrects a grade in two steps and posts the new score with the reason', async () => {
    useGrid(buildGrid());
    const pending = createDeferred();
    const posts = [];
    server.use(
      http.post(`${API}/grade-items/1/corrections`, async ({ request }) => {
        posts.push(await request.json());
        await pending.promise;
        return HttpResponse.json({ id: 9, gradeItemId: 1, kind: 'CORRECTION', previousScore: 7, newScore: 9, reason: 'Error de carga', changedById: 3, changedAt: '2026-11-05T12:00:00' }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<DirectorGradeGrid sectionId="7" kind="PROGRAM" />);
    await openCorrectionForm(user);

    const next = await screen.findByLabelText('Nota nueva');
    await user.type(next, '7');
    await user.type(screen.getByLabelText('Motivo'), 'Error de carga');
    expect(screen.getByText('La nota nueva es igual a la actual.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Revisar cambios' })).toBeDisabled();
    await user.clear(next);
    await user.type(next, '9');
    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }));

    expect(await screen.findByText('Se recalcula')).toBeInTheDocument();
    expect(screen.getByText('+2')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Confirmar corrección' }));
    expect(await screen.findByRole('button', { name: 'Confirmando…' })).toBeDisabled();
    pending.resolve();

    await waitFor(() => expect(toastMock.success).toHaveBeenCalled());
    expect(posts).toEqual([{ newScore: 9, reason: 'Error de carga' }]);
  });

  it('shows the banner and a toast when the server answers that the period ended', async () => {
    useGrid(buildGrid());
    server.use(
      http.post(`${API}/grade-items/1/corrections`, () => HttpResponse.json(errorBody('GRADE_CORRECTION_PERIOD_ENDED'), { status: 409 })),
    );
    const user = userEvent.setup();
    renderWithProviders(<DirectorGradeGrid sectionId="7" kind="PROGRAM" />);
    await openCorrectionForm(user);
    await user.type(await screen.findByLabelText('Nota nueva'), '9');
    await user.type(screen.getByLabelText('Motivo'), 'Error de carga');
    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }));
    await user.click(await screen.findByRole('button', { name: 'Confirmar corrección' }));

    await waitFor(() => expect(toastMock.error).toHaveBeenCalledWith('No se pudo corregir: el cuatrimestre ya terminó.'));
    expect(await screen.findByRole('alert', { hidden: true })).toHaveTextContent('El plazo de corrección terminó');
  });

  it('lists only corrections in the history, newest first, authored by Dirección', async () => {
    useGrid(buildGrid());
    server.use(
      http.get(`${API}/grade-items/1/audit`, () =>
        HttpResponse.json([
          { id: 1, gradeItemId: 1, kind: 'CORRECTION', previousScore: 5, newScore: 6, reason: 'Primera', changedById: 3, changedAt: '2026-09-20T10:00:00' },
          { id: 2, gradeItemId: 1, kind: 'CORRECTION', previousScore: 6, newScore: 7, reason: 'Segunda', changedById: 3, changedAt: '2026-09-22T15:40:00' },
          { id: 3, gradeItemId: 1, kind: 'FINAL_REVERT', previousScore: 7, newScore: 7, reason: 'Otra cosa', changedById: 3, changedAt: '2026-09-23T10:00:00' },
        ]),
      ),
    );
    const user = userEvent.setup();
    renderWithProviders(<DirectorGradeGrid sectionId="7" kind="PROGRAM" />);
    await user.click(await screen.findByRole('button', { name: /Parcial 1 de Benítez, Lucía/ }));
    await user.click(await screen.findByRole('button', { name: 'Historial de Parcial 1' }));

    const items = await screen.findAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('6 → 7');
    expect(items[0]).toHaveTextContent('22/09/2026 15:40');
    expect(items[0]).toHaveTextContent('Dirección');
    expect(screen.queryByText('Otra cosa')).not.toBeInTheDocument();
  });
});
