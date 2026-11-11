import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { installFixedClock } from '@/test/fixedClock';
import { ExamBoardGradesSection } from './ExamBoardGradesSection';

const toastMock = vi.hoisted(() => ({ error: vi.fn(), success: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

const API = 'http://localhost:8080';

globalThis.ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

installFixedClock();

const board = (extra = {}) => ({ id: 6, status: 'SCHEDULED', examDate: '2026-11-03T18:00:00', ...extra });

function registration(id, extra = {}) {
  return {
    id,
    studentName: `Alumno ${id}`,
    dni: `${id}0000`,
    attemptNumber: 1,
    status: 'REGISTERED',
    finalScore: null,
    enrollmentId: 100 + id,
    enrollmentKind: 'PROGRAM',
    priorCondition: 'REGULAR',
    minApprovalGrade: 6,
    gradeItemId: null,
    result: null,
    ...extra,
  };
}

const graded = registration(1, { finalScore: 8, gradeItemId: 9, result: 'PASSED' });
const absentRow = registration(2, { status: 'ABSENT', result: 'ABSENT' });

function renderSection({ mesa = board(), role = 'docente', rows = [graded, absentRow] } = {}) {
  return renderWithProviders(
    <ExamBoardGradesSection mesa={mesa} rows={rows} role={role} canWithdraw={false} onWithdraw={vi.fn()} withdrawing={false} />,
  );
}

const correctButton = () => screen.getByRole('button', { name: 'Corregir la nota de Alumno 1' });

async function fillCorrection(user, { score, reason }) {
  await user.click(correctButton());
  const sheet = await screen.findByRole('dialog', { name: 'Corregir nota del final' });
  if (score) await user.type(within(sheet).getByLabelText('Nota nueva'), score);
  if (reason) await user.type(within(sheet).getByLabelText('Motivo'), reason);
  return sheet;
}

describe('ExamBoardGradesSection correction', () => {
  it('corrects a saved grade in two steps and sends the reason in the final-grades PUT', async () => {
    let body;
    server.use(
      http.put(`${API}/exam-boards/6/final-grades`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ registrations: [] });
      }),
    );
    const user = userEvent.setup();
    renderSection();

    const sheet = await fillCorrection(user, { score: '4,5', reason: 'Error de carga' });
    expect(within(sheet).getByText('14/500 · mínimo 5')).toBeInTheDocument();
    await user.click(within(sheet).getByRole('button', { name: 'Revisar cambios' }));

    expect(within(sheet).getByRole('group', { name: 'Nota del final' })).toHaveTextContent('8');
    expect(within(sheet).getByRole('group', { name: 'Nota del final' })).toHaveTextContent('4,5');
    expect(within(sheet).getByRole('group', { name: 'Nota del final' })).toHaveTextContent('−3,5');
    expect(within(sheet).getByRole('group', { name: 'Resultado' })).toHaveTextContent('Aprobado → Desaprobado');
    expect(within(sheet).getByText('Error de carga')).toBeInTheDocument();
    await user.click(within(sheet).getByRole('button', { name: 'Confirmar corrección' }));

    await vi.waitFor(() => expect(toastMock.success).toHaveBeenCalled());
    expect(body).toEqual({ items: [{ registrationId: 1, score: 4.5, reason: 'Error de carga' }] });
    expect(screen.queryByRole('dialog', { name: 'Corregir nota del final' })).not.toBeInTheDocument();
  });

  it('keeps the review disabled until the reason has at least 5 characters and the grade changes', async () => {
    const user = userEvent.setup();
    renderSection();

    const sheet = await fillCorrection(user, { score: '8', reason: 'Err' });
    const review = within(sheet).getByRole('button', { name: 'Revisar cambios' });

    expect(within(sheet).getByText('La nota nueva es igual a la actual.')).toBeInTheDocument();
    expect(review).toBeDisabled();
    await user.clear(within(sheet).getByLabelText('Nota nueva'));
    await user.type(within(sheet).getByLabelText('Nota nueva'), '9');
    expect(review).toBeDisabled();
    await user.type(within(sheet).getByLabelText('Motivo'), 'or de carga');
    expect(review).toBeEnabled();
  });

  it('turns a saved grade into Ausente with a reason and no score', async () => {
    let body;
    server.use(
      http.put(`${API}/exam-boards/6/final-grades`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ registrations: [] });
      }),
    );
    const user = userEvent.setup();
    renderSection();

    const sheet = await fillCorrection(user, { reason: 'No se presentó' });
    await user.click(within(sheet).getByRole('button', { name: 'Marcar Ausente a Alumno 1' }));
    expect(within(sheet).getByLabelText('Nota nueva')).toBeDisabled();
    await user.click(within(sheet).getByRole('button', { name: 'Revisar cambios' }));
    expect(within(sheet).getByRole('group', { name: 'Resultado' })).toHaveTextContent('Aprobado → Ausente');
    await user.click(within(sheet).getByRole('button', { name: 'Confirmar corrección' }));

    await vi.waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('Alumno 1 quedó Ausente. El cambio quedó en el historial.'));
    expect(body).toEqual({ items: [{ registrationId: 1, absent: true, reason: 'No se presentó' }] });
  });

  it('keeps the sheet open and shows the rejection when the backend refuses the correction', async () => {
    server.use(
      http.put(`${API}/exam-boards/6/final-grades`, () =>
        HttpResponse.json(
          { ...errorBody('EXAM_BOARD_FINAL_GRADE_ROW_REJECTED'), details: { registrationId: 1, causeCode: 'EXAM_BOARD_CLOSED', causeDetails: {} } },
          { status: 409 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderSection();

    const sheet = await fillCorrection(user, { score: '5', reason: 'Error de carga' });
    await user.click(within(sheet).getByRole('button', { name: 'Revisar cambios' }));
    await user.click(within(sheet).getByRole('button', { name: 'Confirmar corrección' }));

    expect(await within(sheet).findByRole('alert')).toHaveTextContent('No se guardó ninguna nota');
    expect(screen.getByRole('dialog', { name: 'Corregir nota del final' })).toBeInTheDocument();
  });

  it('blocks the correction on a closed board and warns about it', () => {
    renderSection({ mesa: board({ status: 'CLOSED' }) });

    expect(correctButton()).toBeDisabled();
    expect(screen.getByText('Acta cerrada.')).toBeInTheDocument();
  });

  it('does not offer the correction before the exam date nor to roles that do not grade', () => {
    const future = renderSection({ mesa: board({ examDate: '2026-11-20T18:00:00' }) });
    expect(screen.queryByRole('button', { name: 'Corregir la nota de Alumno 1' })).not.toBeInTheDocument();
    future.unmount();

    renderSection({ role: 'administrativo' });
    expect(screen.queryByRole('button', { name: 'Corregir la nota de Alumno 1' })).not.toBeInTheDocument();
  });

  it('tells that a saved Ausente can only turn into a grade', () => {
    renderSection();

    expect(screen.getByText('Ausente guardado: solo puede pasar a una nota')).toBeInTheDocument();
  });
});
