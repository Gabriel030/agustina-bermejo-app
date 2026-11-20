import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { errorBody } from '@/test/fixtures/errors';
import { renderWithProviders } from '@/test/renderWithProviders';
import { closureConfirmPath, closureSheet, closureSheetPath, closureSheetWithUnpublishedExam, closureStudents, mockClosureSheetEndpoints } from '@/test/fixtures/closureSheet';
import { DocenteClosureSheet } from './DocenteClosureSheet';

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

const API = 'http://localhost:8080';

function renderSheet(sheet = closureSheet(), route = '/docente/mis-comisiones/7/cierre') {
  mockClosureSheetEndpoints(server, sheet, { kind: route.includes('COURSE') ? 'course' : 'program' });
  return renderWithProviders(
    <Routes>
      <Route path="/docente/mis-comisiones/:sectionId/cierre" element={<DocenteClosureSheet />} />
    </Routes>,
    { route },
  );
}

function rowOf(name) {
  return screen.getByRole('rowheader', { name }).closest('tr');
}

describe('DocenteClosureSheet', () => {
  it('showsOneResultPillPerStudentRowAndStrikesWithdrawnStudents', async () => {
    renderSheet();

    await screen.findByRole('rowheader', { name: 'Acosta, Lucía' });
    expect(within(rowOf('Acosta, Lucía')).getByText('Promociona')).toBeInTheDocument();
    expect(within(rowOf('Benítez, Tomás')).getByText('Promoción condicionada')).toBeInTheDocument();
    const withdrawn = rowOf('Aguilar, Melina');
    expect(within(withdrawn).getByText('Baja')).toBeInTheDocument();
    expect(within(withdrawn).getByText('Aguilar, Melina').className).toContain('line-through');
    expect(within(withdrawn).getByText('Sin cómputo')).toBeInTheDocument();
  });

  it('showsRetakeAsEffectiveScoreWithTheOriginalCrossedOut', async () => {
    renderSheet();

    await screen.findByRole('rowheader', { name: 'Cabrera, Julieta' });
    expect(within(rowOf('Cabrera, Julieta')).getByLabelText('7, recuperatorio, reemplaza al 6')).toBeInTheDocument();
    expect(within(rowOf('Cabrera, Julieta')).getByText('1/1')).toBeInTheDocument();
  });

  it('showsRequirementsAsMarksWithAccessibleNames', async () => {
    renderSheet();

    await screen.findByRole('rowheader', { name: 'Domínguez, Martín' });
    const row = rowOf('Domínguez, Martín');
    expect(within(row).getByRole('img', { name: 'Correlativas: no cumple' })).toBeInTheDocument();
    expect(within(row).getByRole('img', { name: 'TP aprobados: cumple' })).toBeInTheDocument();
    expect(within(rowOf('Herrera, Joaquín')).getByRole('img', { name: 'Pagos al día: no verificado' })).toBeInTheDocument();
  });

  it('neverShowsAmountsNorWhichDocumentIsMissingToTheTeacher', async () => {
    const { container } = renderSheet();

    await screen.findByRole('rowheader', { name: 'Benítez, Tomás' });
    expect(within(rowOf('Benítez, Tomás')).getByRole('img', { name: 'Legajo completo: no cumple' })).toBeInTheDocument();
    expect(container.textContent).not.toContain('Analítico');
    expect(container.textContent).not.toMatch(/\$\s?\d/);
  });

  it('filtersTheSheetWhenAKpiCardIsPressedAndClearsItOnSecondPress', async () => {
    const user = userEvent.setup();
    renderSheet();
    await screen.findByRole('rowheader', { name: 'Acosta, Lucía' });

    const card = screen.getByRole('button', { name: /^Libres por inasistencias: 1/ });
    await user.click(card);

    expect(card).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('rowheader', { name: 'Herrera, Joaquín' })).toBeInTheDocument();
    expect(screen.queryByRole('rowheader', { name: 'Acosta, Lucía' })).not.toBeInTheDocument();
    expect(screen.queryByRole('rowheader', { name: 'Aguilar, Melina' })).not.toBeInTheDocument();

    await user.click(card);

    expect(card).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('rowheader', { name: 'Acosta, Lucía' })).toBeInTheDocument();
  });

  it('opensThePrerequisitesDrawerWithSubjectCodes', async () => {
    const user = userEvent.setup();
    renderSheet();
    await screen.findByRole('rowheader', { name: 'Domínguez, Martín' });

    await user.click(within(rowOf('Domínguez, Martín')).getByRole('button', { name: /Correlativa pendiente: ver Administración/ }));

    const drawer = await screen.findByRole('dialog', { name: 'Correlativas de Domínguez, Martín' });
    expect(await within(drawer).findByText('ANAT')).toBeInTheDocument();
    expect(within(drawer).getByText('Cursada regular')).toBeInTheDocument();
    expect(within(drawer).getByText('Exceptuada')).toBeInTheDocument();
    expect(within(drawer).getByText('Falta cumplir una correlativa.', { exact: false })).toBeInTheDocument();
  });

  it('blocksConfirmationAndExplainsWhichExamIsUnpublished', async () => {
    renderSheet(closureSheetWithUnpublishedExam());

    expect(await screen.findByText('Falta publicar: 2.° parcial.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirmar cierre' })).toBeDisabled();
    expect(screen.getByText('No se puede confirmar hasta publicar: 2.° parcial.')).toBeInTheDocument();
  });

  it('confirmsTheClosureAfterTheDialogAndRefreshesTheSheet', async () => {
    const user = userEvent.setup();
    const confirmed = vi.fn();
    renderSheet();
    server.use(http.post(closureConfirmPath(), () => {
      confirmed();
      return new HttpResponse(null, { status: 204 });
    }));
    await screen.findByRole('rowheader', { name: 'Acosta, Lucía' });

    await user.click(screen.getByRole('button', { name: 'Confirmar cierre' }));
    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar cierre' }));

    await waitFor(() => expect(confirmed).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('Entregaste la planilla de cierre.'));
  });

  it('showsTheSpanishMessageOfTheDomainErrorWhenConfirmationFails', async () => {
    const user = userEvent.setup();
    renderSheet();
    server.use(http.post(closureConfirmPath(), () =>
      HttpResponse.json(errorBody('PUBLISHED_GRADES_INCOMPLETE'), { status: 409 })));
    await screen.findByRole('rowheader', { name: 'Acosta, Lucía' });

    await user.click(screen.getByRole('button', { name: 'Confirmar cierre' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Confirmar cierre' }));

    await waitFor(() => expect(toastMock.error).toHaveBeenCalledWith('Faltan publicar evaluaciones: no se puede confirmar el cierre todavía.'));
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
  });

  it('hidesTheConfirmationBarOnceTheClosureIsClosed', async () => {
    renderSheet(closureSheet({ status: 'CLOSED', availability: { canConfirm: false, canApprove: false, blockedReason: 'ALREADY_CLOSED' } }));

    expect(await screen.findByText('Cerrado')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirmar cierre' })).not.toBeInTheDocument();
  });

  it('readsTheCourseEndpointWhenTheRouteIsACourse', async () => {
    renderSheet(closureSheet({ students: [closureStudents.promotes] }), '/docente/mis-comisiones/7/cierre?type=COURSE');

    expect(await screen.findByRole('rowheader', { name: 'Acosta, Lucía' })).toBeInTheDocument();
  });

  it('offersRetryWhenTheSheetFailsToLoad', async () => {
    server.use(http.get(closureSheetPath(), () => new HttpResponse(null, { status: 500 })));
    renderWithProviders(
      <Routes>
        <Route path="/docente/mis-comisiones/:sectionId/cierre" element={<DocenteClosureSheet />} />
      </Routes>,
      { route: '/docente/mis-comisiones/7/cierre' },
    );

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
