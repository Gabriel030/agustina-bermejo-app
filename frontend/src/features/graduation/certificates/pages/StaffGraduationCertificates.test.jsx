import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { axe } from '@/test/axe';
import { errorBody } from '@/test/fixtures/errors';
import { eligibleGraduate, graduationCertificateBatch, springPage } from '@/test/fixtures/certificates';
import { BATCH_LIMIT } from '@/features/graduation/certificates/lib/staffGraduationCertificates';
import { certificateErrorResponse } from '@/test/mocks/certificateErrorHandlers';
import { StaffGraduationCertificates } from '@/features/graduation/certificates/pages/StaffGraduationCertificates';

const API = 'http://localhost:8080';
const auth = vi.hoisted(() => ({ roles: ['ADMINISTRATIVO'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { roles: auth.roles } }),
}));

function eligibleList(rows) {
  server.use(http.get(`${API}/graduation-certificates/eligible`, () => HttpResponse.json(springPage(rows))));
}

const pendingRow = eligibleGraduate({ enrollmentId: 11, studentFullName: 'Gómez, Tomás', status: 'PENDING' });
const blockedRow = eligibleGraduate({ enrollmentId: 12, studentFullName: 'Ruiz, Lucía', status: 'BLOCKED_BY_DEBT' });

function renderPage() {
  return renderWithProviders(<StaffGraduationCertificates />);
}

describe('StaffGraduationCertificates', () => {
  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:preview');
    URL.revokeObjectURL = vi.fn();
  });

  it('shows the empty state when there are no eligible graduates', async () => {
    eligibleList([]);
    renderPage();

    expect(await screen.findByText('No hay egresados para mostrar')).toBeInTheDocument();
  });

  it('shows the error state when the eligible list fails to load', async () => {
    server.use(http.get(`${API}/graduation-certificates/eligible`, () => HttpResponse.json(errorBody('INTERNAL'), { status: 500 })));
    renderPage();

    expect(await screen.findByText('No pudimos cargar los egresados')).toBeInTheDocument();
  });

  it('shows the loading state while the list is pending', () => {
    server.use(http.get(`${API}/graduation-certificates/eligible`, () => new Promise(() => {})));
    renderPage();

    expect(screen.getByLabelText('Cargando')).toBeInTheDocument();
  });

  it('lets the user select pending graduates but not those blocked by debt', async () => {
    eligibleList([pendingRow, blockedRow]);
    renderPage();

    expect(await screen.findByRole('checkbox', { name: 'Seleccionar a Gómez, Tomás' })).toBeEnabled();
    expect(screen.getByRole('checkbox', { name: 'Seleccionar a Ruiz, Lucía' })).toBeDisabled();
    expect(screen.getByText('Bloqueado por deuda')).toBeInTheDocument();
  });

  it('issues the selected pending graduates in one batch after confirming and shows partial results', async () => {
    const user = userEvent.setup();
    let sentItems;
    eligibleList([pendingRow, eligibleGraduate({ enrollmentId: 12, studentFullName: 'Pérez, Ana', status: 'PENDING' })]);
    server.use(
      http.post(`${API}/graduation-certificates/batch`, async ({ request }) => {
        sentItems = (await request.json()).items;
        return HttpResponse.json(graduationCertificateBatch());
      }),
    );
    renderPage();

    await user.click(await screen.findByRole('checkbox', { name: 'Seleccionar a Gómez, Tomás' }));
    await user.click(screen.getByRole('checkbox', { name: 'Seleccionar a Pérez, Ana' }));
    await user.click(screen.getByRole('button', { name: 'Emitir seleccionados' }));

    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar y emitir 2' }));

    expect(await screen.findByText((_, element) => element?.tagName === 'P' && element.textContent === 'Emitidos 1 de 2')).toBeInTheDocument();
    expect(sentItems).toEqual([
      { enrollmentId: 11, kind: 'PROGRAM' },
      { enrollmentId: 12, kind: 'PROGRAM' },
    ]);
    expect(screen.getByText('Pérez, Ana:')).toBeInTheDocument();
    expect(screen.getByText('El alumno tiene deuda pendiente: no se puede emitir el certificado de egreso.')).toBeInTheDocument();
  });

  it('shows the domain message when the preview is rejected', async () => {
    const user = userEvent.setup();
    eligibleList([pendingRow]);
    server.use(http.post(`${API}/graduation-certificates/preview`, () => certificateErrorResponse(409, 'GRADUATION_CERTIFICATE_NOT_ELIGIBLE')));
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Vista previa de Gómez, Tomás' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No se puede emitir el certificado de egreso en este estado.');
  });

  it('opens the preview dialog with the generated PDF', async () => {
    const user = userEvent.setup();
    eligibleList([pendingRow]);
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Vista previa de Gómez, Tomás' }));

    const frame = await screen.findByTitle('Vista previa del certificado de Gómez, Tomás');
    expect(frame).toHaveAttribute('src', 'blob:preview');
  });

  it('does not send the batch when the confirmation is cancelled', async () => {
    const user = userEvent.setup();
    const batchRequested = vi.fn();
    eligibleList([pendingRow]);
    server.use(
      http.post(`${API}/graduation-certificates/batch`, () => {
        batchRequested();
        return HttpResponse.json(graduationCertificateBatch());
      }),
    );
    renderPage();

    await user.click(await screen.findByRole('checkbox', { name: 'Seleccionar a Gómez, Tomás' }));
    await user.click(screen.getByRole('button', { name: 'Emitir seleccionados' }));
    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(batchRequested).not.toHaveBeenCalled();
  });

  it('stops selecting at the batch limit', async () => {
    const user = userEvent.setup({ delay: null });
    const rows = Array.from({ length: BATCH_LIMIT + 1 }, (_, index) =>
      eligibleGraduate({ enrollmentId: index + 1, studentFullName: `Alumno ${index + 1}`, status: 'PENDING' }),
    );
    eligibleList(rows);
    renderPage();

    for (let index = 1; index <= BATCH_LIMIT; index += 1) {
      await user.click(await screen.findByRole('checkbox', { name: `Seleccionar a Alumno ${index}` }));
    }

    expect(screen.getByRole('checkbox', { name: `Seleccionar a Alumno ${BATCH_LIMIT + 1}` })).toBeDisabled();
    expect(screen.getByText(`${BATCH_LIMIT} seleccionados · máximo ${BATCH_LIMIT}`)).toBeInTheDocument();
  }, 60000);

  it('has no accessibility violations', async () => {
    eligibleList([pendingRow, blockedRow]);
    const { container } = renderPage();

    await screen.findByRole('checkbox', { name: 'Seleccionar a Gómez, Tomás' });
    expect(await axe(container)).toHaveNoViolations();
  });
});
