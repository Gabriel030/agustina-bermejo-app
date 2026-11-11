import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import {
  closedClosureSheet,
  closureActionPath,
  closureExceptionPath,
  closureSheet,
  closureSheetPath,
  closureSheetResponse,
  closureStudents,
  mockClosureSheetEndpoints,
} from '@/test/fixtures/closureSheet';
import { StaffClosureSheet } from './StaffClosureSheet';

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

function renderSheet(role, sheet = closureSheet()) {
  mockClosureSheetEndpoints(server, sheet);
  return renderWithProviders(
    <Routes>
      <Route path="/:role/cierre-de-comision/:sectionId" element={<StaffClosureSheet role={role} />} />
    </Routes>,
    { route: `/${role}/cierre-de-comision/7` },
  );
}

const confirmedSheet = () => closureSheet({ status: 'TEACHER_CONFIRMED', availability: { canConfirm: false, canApprove: true } });

function rowOf(name) {
  return screen.getByRole('rowheader', { name }).closest('tr');
}

describe('StaffClosureSheet', () => {
  it('showsMissingDocumentsAndNoTeacherActionsForPreceptor', async () => {
    renderSheet('preceptor', confirmedSheet());

    await screen.findByRole('rowheader', { name: 'Benítez, Tomás' });
    expect(within(rowOf('Benítez, Tomás')).getByText('falta Analítico')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Aprobar y cerrar|Reabrir|Exceptuar/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Confirmar cierre/ })).not.toBeInTheDocument();
  });

  it('strikesWithdrawnStudentsAndKeepsOnePillPerRow', async () => {
    renderSheet('preceptor');

    await screen.findByRole('rowheader', { name: 'Aguilar, Melina' });
    const withdrawn = rowOf('Aguilar, Melina');
    expect(within(withdrawn).getByText('Aguilar, Melina').className).toContain('line-through');
    expect(within(withdrawn).getAllByText('Baja')).toHaveLength(1);
  });

  it('requiresBookAndFolioBeforeReachingTheSecondApprovalStep', async () => {
    const user = userEvent.setup();
    renderSheet('administrativo', confirmedSheet());

    await user.click(await screen.findByRole('button', { name: 'Aprobar y cerrar' }));
    await user.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Cargá el libro y el folio donde se asienta el acta.');
    expect(screen.queryByText('Paso 2 de 2', { exact: false })).not.toBeInTheDocument();
  });

  it('approvesWithBookAndFolioAfterTwoStepsAndShowsTheReportNumber', async () => {
    const user = userEvent.setup();
    let body = null;
    renderSheet('administrativo', confirmedSheet());
    server.use(
      http.post(closureActionPath('approve'), async ({ request }) => {
        body = await request.json();
        server.use(http.get(closureSheetPath(), () => closureSheetResponse(closedClosureSheet())));
        return new HttpResponse(null, { status: 204 });
      }),
    );

    await user.click(await screen.findByRole('button', { name: 'Aprobar y cerrar' }));
    await user.type(screen.getByLabelText('Libro'), 'L28');
    await user.type(screen.getByLabelText('Folio'), '93');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    expect(screen.getByText('Paso 2 de 2', { exact: false })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Aprobar y cerrar' }));

    expect(await screen.findByText('Acta ACTA-2026-0042 · versión 1 · Libro L28 · Folio 93')).toBeInTheDocument();
    expect(body).toEqual({ book: 'L28', folio: '93', closeWithoutTeacherConfirmation: false });
    expect(screen.queryByRole('button', { name: 'Aprobar y cerrar' })).not.toBeInTheDocument();
  });

  it('marksClosingWithoutTeacherConfirmationWhenTheTeacherDidNotConfirm', async () => {
    const user = userEvent.setup();
    let body = null;
    renderSheet('administrativo', closureSheet({ availability: { canConfirm: true, canApprove: true } }));
    server.use(
      http.post(closureActionPath('approve'), async ({ request }) => {
        body = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );

    await user.click(await screen.findByRole('button', { name: 'Cerrar sin confirmación del docente' }));
    await user.type(screen.getByLabelText('Libro'), 'L1');
    await user.type(screen.getByLabelText('Folio'), '2');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await user.click(screen.getByRole('button', { name: 'Cerrar el acta' }));

    await waitFor(() => expect(body).toEqual({ book: 'L1', folio: '2', closeWithoutTeacherConfirmation: true }));
  });

  it('disablesApprovalAndExplainsWhyWhenGradesAreUnpublished', async () => {
    renderSheet('administrativo', closureSheet({
      columns: [{ type: 'EXAM', sequenceNumber: 1, published: true }, { type: 'EXAM', sequenceNumber: 2, published: false }],
      availability: { canConfirm: false, canApprove: false, blockedReason: 'PUBLISHED_GRADES_INCOMPLETE' },
    }));

    expect(await screen.findByRole('button', { name: 'Cerrar sin confirmación del docente' })).toBeDisabled();
    expect(screen.getByText('No se puede cerrar hasta publicar: 2.° parcial.')).toBeInTheDocument();
  });

  it('rejectsAnExceptionReasonShorterThanTenCharactersAndSendsAValidOne', async () => {
    const user = userEvent.setup();
    let body = null;
    renderSheet('director');
    server.use(
      http.put(closureExceptionPath(3), async ({ request }) => {
        body = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );

    await user.click(await screen.findByRole('button', { name: 'Exceptuar a Benítez, Tomás' }));
    await user.type(screen.getByPlaceholderText('Motivo de la excepción'), 'corto');
    await user.click(screen.getByRole('button', { name: 'Exceptuar' }));
    expect(screen.getByRole('alert')).toHaveTextContent('al menos 10 caracteres');
    expect(body).toBeNull();

    await user.type(screen.getByPlaceholderText('Motivo de la excepción'), ' motivo suficiente');
    await user.click(screen.getByRole('button', { name: 'Exceptuar' }));

    await waitFor(() => expect(body).toEqual({ reason: 'corto motivo suficiente' }));
  });

  it('offersToRevokeAnExistingException', async () => {
    const user = userEvent.setup();
    let called = false;
    const exempted = { ...closureStudents.conditional, promotionExempted: true };
    renderSheet('director', closureSheet({ students: [exempted] }));
    server.use(
      http.delete(closureExceptionPath(3), () => {
        called = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    expect(await screen.findByText('★ Exceptuado por Dirección')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Exceptuar a/ })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Quitar la excepción de Benítez, Tomás' }));
    await user.click(screen.getAllByRole('button', { name: 'Quitar excepción' }).at(-1));

    await waitFor(() => expect(called).toBe(true));
  });

  it('hidesExceptionActionsOnAClosedSheetAndReopensWithAReason', async () => {
    const user = userEvent.setup();
    let body = null;
    renderSheet('director', closedClosureSheet({ students: [closureStudents.conditional] }));
    server.use(
      http.post(closureActionPath('reopen'), async ({ request }) => {
        body = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );

    await screen.findByText('Acta ACTA-2026-0042 · versión 1 · Libro L28 · Folio 93');
    expect(screen.queryByRole('button', { name: /^Exceptuar a/ })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reabrir con motivo' }));
    await user.type(screen.getByPlaceholderText('Motivo de la reapertura'), 'corrección de nota del parcial');
    await user.click(screen.getByRole('button', { name: 'Reabrir' }));

    await waitFor(() => expect(body).toEqual({ reason: 'corrección de nota del parcial' }));
  });

  it('doesNotShowDirectorActionsToTheAdministrativo', async () => {
    renderSheet('administrativo', closedClosureSheet());

    await screen.findByText('Acta ACTA-2026-0042 · versión 1 · Libro L28 · Folio 93');
    expect(screen.queryByRole('button', { name: 'Reabrir con motivo' })).not.toBeInTheDocument();
  });
});
