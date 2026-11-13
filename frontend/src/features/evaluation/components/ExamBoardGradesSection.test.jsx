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

function board(extra = {}) {
  return { id: 6, status: 'SCHEDULED', examDate: '2026-11-03T18:00:00', ...extra };
}

function registration(id, extra = {}) {
  return {
    id,
    studentName: `Alumno ${id}`,
    dni: `${id}0000`,
    carrera: 'PEI',
    attemptNumber: 1,
    status: 'REGISTERED',
    finalScore: null,
    enrollmentId: 100 + id,
    enrollmentKind: 'PROGRAM',
    priorCondition: 'REGULAR',
    finalExamEligibility: { eligible: true, reason: null },
    minApprovalGrade: 6,
    gradeItemId: null,
    result: null,
    ...extra,
  };
}

function renderSection({ mesa = board(), rows, role = 'docente', canWithdraw = false } = {}) {
  return renderWithProviders(
    <ExamBoardGradesSection mesa={mesa} rows={rows ?? [registration(1), registration(2), registration(3)]} role={role} canWithdraw={canWithdraw} onWithdraw={vi.fn()} withdrawing={false} />,
  );
}

const gradeInput = (id) => screen.getByLabelText(`Nota del final de Alumno ${id}`);
const absentToggle = (id) => screen.getByRole('button', { name: `Marcar ausente a Alumno ${id}` });

describe('ExamBoardGradesSection', () => {
  it('shows the approval threshold and the scale with comma', () => {
    renderSection();

    expect(screen.getByText('Aprueba con 6 o más')).toBeInTheDocument();
    expect(screen.getByText(/admite coma \(7,5\)/)).toBeInTheDocument();
  });

  it('moves to the next row with Enter and marks absent with the A key', async () => {
    const user = userEvent.setup();
    renderSection();

    await user.click(gradeInput(1));
    await user.keyboard('7{Enter}');
    expect(gradeInput(2)).toHaveFocus();
    await user.keyboard('a');

    expect(absentToggle(2)).toHaveAttribute('aria-pressed', 'true');
    expect(gradeInput(2)).toBeDisabled();
    expect(gradeInput(3)).toHaveFocus();
  });

  it('keeps a grade and the absent mark mutually exclusive', async () => {
    const user = userEvent.setup();
    renderSection();

    await user.type(gradeInput(1), '8');
    await user.click(absentToggle(1));
    expect(gradeInput(1)).toHaveValue('');
    expect(gradeInput(1)).toBeDisabled();

    await user.click(absentToggle(1));
    expect(gradeInput(1)).toBeEnabled();
    expect(absentToggle(1)).toHaveAttribute('aria-pressed', 'false');
  });

  it('derives a single result pill per row from the threshold', async () => {
    const user = userEvent.setup();
    renderSection();

    await user.type(gradeInput(1), '7,5');
    await user.type(gradeInput(2), '4');

    expect(screen.getByText('Aprobado')).toBeInTheDocument();
    expect(screen.getByText('Desaprobado')).toBeInTheDocument();
    expect(screen.getAllByText('Pendiente')).toHaveLength(1);
  });

  it('flags a grade outside 1 to 10 and blocks the review', async () => {
    const user = userEvent.setup();
    renderSection();

    await user.type(gradeInput(1), '11');

    expect(gradeInput(1)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('Entre 1 y 10')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Revisar y guardar' })).toBeDisabled();
  });

  it('reviews the whole batch with Today, New and Difference and saves it atomically with a decimal point', async () => {
    let body;
    server.use(
      http.put(`${API}/exam-boards/6/final-grades`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ registrations: [registration(1, { finalScore: 7.5, gradeItemId: 9, result: 'PASSED', status: 'REGISTERED' }), registration(2, { status: 'ABSENT', result: 'ABSENT' })] });
      }),
    );
    const user = userEvent.setup();
    renderSection();

    await user.type(gradeInput(1), '7,5');
    await user.click(absentToggle(2));
    await user.click(screen.getByRole('button', { name: 'Revisar y guardar' }));

    const sheet = await screen.findByRole('dialog', { name: 'Revisá antes de guardar' });
    expect(within(sheet).getByRole('group', { name: 'Alumno 1' })).toHaveTextContent('Sin nota');
    expect(within(sheet).getByRole('group', { name: 'Alumno 1' })).toHaveTextContent('7,5');
    expect(within(sheet).getByRole('group', { name: 'Alumno 2' })).toHaveTextContent('Pendiente → Ausente');

    await user.click(within(sheet).getByRole('button', { name: 'Guardar las notas' }));

    await vi.waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('Se guardaron 1 nota y 1 ausente.'));
    expect(body).toEqual({ items: [{ registrationId: 1, score: 7.5 }, { registrationId: 2, absent: true }] });
    expect(screen.queryByRole('dialog', { name: 'Revisá antes de guardar' })).not.toBeInTheDocument();
  });

  it('keeps every draft, marks the rejected row and offers to drop it when the batch fails', async () => {
    server.use(
      http.put(`${API}/exam-boards/6/final-grades`, () =>
        HttpResponse.json(
          { ...errorBody('EXAM_BOARD_FINAL_GRADE_ROW_REJECTED'), details: { registrationId: 2, causeCode: 'STUDENT_NOT_REGULAR', causeDetails: {} } },
          { status: 409 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderSection();

    await user.type(gradeInput(1), '7');
    await user.type(gradeInput(2), '5');
    await user.click(screen.getByRole('button', { name: 'Revisar y guardar' }));
    await user.click(await screen.findByRole('button', { name: 'Guardar las notas' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('No se guardó ninguna nota: El alumno no tiene la cursada regular.');
    expect(gradeInput(2)).toHaveAttribute('aria-invalid', 'true');
    expect(gradeInput(1)).toHaveValue('7');

    await user.click(within(alert).getByRole('button', { name: 'Quitar a Alumno 2 y revisar el resto' }));

    expect(gradeInput(2)).toHaveValue('');
    expect(screen.getByRole('dialog', { name: 'Revisá antes de guardar' })).toHaveTextContent('Alumno 1');
    expect(screen.getByRole('dialog', { name: 'Revisá antes de guardar' })).not.toHaveTextContent('Alumno 2');
  });

  it('asks for confirmation before discarding the changes', async () => {
    const user = userEvent.setup();
    renderSection();

    await user.type(gradeInput(1), '7');
    await user.click(screen.getByRole('button', { name: 'Descartar' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Descartar' }));

    expect(gradeInput(1)).toHaveValue('');
    expect(screen.queryByRole('button', { name: 'Revisar y guardar' })).not.toBeInTheDocument();
  });

  it('disables the controls with the reason and the date when the exam has not been taken yet', () => {
    renderSection({ mesa: board({ examDate: '2026-11-12T18:00:00' }) });

    expect(gradeInput(1)).toBeDisabled();
    expect(absentToggle(1)).toBeDisabled();
    expect(screen.getByText(/La carga de notas se habilita después del examen, el .*2026\./)).toBeInTheDocument();
  });

  it('shows the closed board as read only without inputs', () => {
    renderSection({ mesa: board({ status: 'CLOSED' }), rows: [registration(1, { status: 'GRADED', finalScore: 8, result: 'PASSED' })] });

    expect(screen.queryByLabelText(/Nota del final de/)).not.toBeInTheDocument();
    expect(screen.getByText('Las notas quedaron firmes y ya no se pueden corregir. Si hay un error, hablá con Dirección.')).toBeInTheDocument();
    expect(screen.getByText('8')).toBeInTheDocument();
  });

  it('shows a canceled board without inputs', () => {
    renderSection({ mesa: board({ status: 'CANCELED' }) });

    expect(screen.queryByLabelText(/Nota del final de/)).not.toBeInTheDocument();
  });

  it.each(['preceptor', 'administrativo', 'director'])('shows the %s the sheet read only', (role) => {
    renderSection({ role });

    expect(screen.queryByLabelText(/Nota del final de/)).not.toBeInTheDocument();
    expect(screen.getByText('Las notas las carga el tribunal de la mesa.')).toBeInTheDocument();
  });

  it('offers withdrawing only to the roles that manage the board', () => {
    renderSection({ role: 'preceptor', canWithdraw: true });

    expect(screen.getByRole('button', { name: 'Dar de baja a Alumno 1' })).toBeInTheDocument();
  });

  it('shows a student who is not regular as blocked, with a single Sin cursada regular pill', () => {
    renderSection({ rows: [registration(1, { priorCondition: 'FREE' })] });

    expect(screen.getByText('Sin cursada regular')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /Requiere cursada regular/ })).toBeInTheDocument();
    expect(screen.queryByLabelText('Nota del final de Alumno 1')).not.toBeInTheDocument();
  });

  it('does not list withdrawn registrations', () => {
    renderSection({ rows: [registration(1), registration(2, { status: 'WITHDRAWN' })] });

    expect(screen.queryByText('Alumno 2')).not.toBeInTheDocument();
  });
});
