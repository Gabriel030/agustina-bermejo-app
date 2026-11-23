import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { axe } from '@/test/axe';
import { errorBody } from '@/test/fixtures/errors';
import { examCertificate, examCertificateBatch, examCertificateCandidates } from '@/test/fixtures/constancias';
import { ExamCertificatesSection } from './ExamCertificatesSection';

const API = 'http://localhost:8080';
const CANDIDATES_URL = `${API}/exam-boards/9/exam-certificates/candidates`;
const BATCH_URL = `${API}/exam-boards/9/exam-certificates/batch`;

function candidate(overrides) {
  return {
    registrationId: 60,
    studentId: 8,
    studentName: 'Juan Pérez',
    maskedDocument: '***.***.555',
    availability: 'AVAILABLE',
    certificateId: null,
    ...overrides,
  };
}

function renderSection(role = 'docente') {
  return renderWithProviders(<ExamCertificatesSection mesaId={9} role={role} />);
}

describe('ExamCertificatesSection', () => {
  it('lists eligible students with a checkbox and the others only with their reason', async () => {
    renderSection();

    expect(await screen.findByRole('checkbox', { name: 'Seleccionar a Ana Paz' })).toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: 'Seleccionar a Luis Gómez' })).not.toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: 'Seleccionar a Marta Ríos' })).not.toBeInTheDocument();
    expect(screen.getByText('Para emitir')).toBeInTheDocument();
    expect(screen.getByText('Emitida')).toBeInTheDocument();
    expect(screen.getByText('Ausente')).toBeInTheDocument();
    expect(screen.queryByText(/\$|Administración|excepción/i)).not.toBeInTheDocument();
  });

  it('keeps the issue button disabled until somebody is selected and counts the selection', async () => {
    const user = userEvent.setup();
    renderSection();

    expect(await screen.findByRole('button', { name: 'Emitir constancias' })).toBeDisabled();
    expect(screen.getByText('Marcá a quién emitirle la constancia.')).toBeInTheDocument();

    await user.click(screen.getByRole('checkbox', { name: 'Seleccionar a Ana Paz' }));

    expect(screen.getByRole('button', { name: 'Emitir constancias (1)' })).toBeEnabled();
  });

  it('says there is nobody to issue when no student is eligible', async () => {
    server.use(http.get(CANDIDATES_URL, () => HttpResponse.json([candidate({ availability: 'ABSENT' })])));
    renderSection();

    expect(await screen.findByText('No hay alumnos para emitir.')).toBeInTheDocument();
    expect(screen.queryByText('Marcá a quién emitirle la constancia.')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Emitir constancias' })).toBeDisabled();
  });

  it('labels the actions column for assistive technology', async () => {
    renderSection('director');

    await screen.findByText('Emitida');

    expect(screen.getByRole('columnheader', { name: 'Acciones' })).toBeInTheDocument();
  });

  it('moves the focus to the result notice after issuing', async () => {
    const user = userEvent.setup();
    renderSection();

    await user.click(await screen.findByRole('checkbox', { name: 'Seleccionar a Ana Paz' }));
    await user.click(screen.getByRole('button', { name: 'Emitir constancias (1)' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Emitir' }));

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('emitida'));
    await waitFor(() => expect(screen.getByRole('status').firstElementChild).toHaveFocus());
  });

  it('moves the focus to the result notice after revoking', async () => {
    const user = userEvent.setup();
    renderSection('director');

    await user.click(await screen.findByRole('button', { name: 'Revocar constancia de Luis Gómez' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByPlaceholderText('Motivo'), 'Error en el DNI');
    await user.click(within(dialog).getByRole('button', { name: 'Revocar constancia' }));

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Constancia revocada.'));
    await waitFor(() => expect(screen.getByRole('status').firstElementChild).toHaveFocus());
  });

  it('selects every eligible student with the select all checkbox', async () => {
    server.use(
      http.get(CANDIDATES_URL, () =>
        HttpResponse.json([candidate({ registrationId: 60 }), candidate({ registrationId: 61, studentName: 'Sofía Luna' })]),
      ),
    );
    const user = userEvent.setup();
    renderSection();

    await user.click(await screen.findByRole('checkbox', { name: 'Seleccionar todos los elegibles (2)' }));

    expect(screen.getByRole('checkbox', { name: 'Seleccionar a Juan Pérez' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Seleccionar a Sofía Luna' })).toBeChecked();
    expect(screen.getByRole('button', { name: 'Emitir constancias (2)' })).toBeEnabled();
  });

  it('issues the batch after confirming and reports the result in the page notice', async () => {
    let body;
    server.use(
      http.post(BATCH_URL, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(
          examCertificateBatch({
            issuedCount: 1,
            skippedCount: 0,
            items: [{ registrationId: 55, studentName: 'Ana Paz', outcome: 'ISSUED', certificateId: 13 }],
          }),
        );
      }),
    );
    const user = userEvent.setup();
    renderSection();

    await user.click(await screen.findByRole('checkbox', { name: 'Seleccionar a Ana Paz' }));
    await user.click(screen.getByRole('button', { name: 'Emitir constancias (1)' }));
    const dialog = await screen.findByRole('alertdialog');
    expect(within(dialog).getByText('Emitir 1 constancia')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Emitir' }));

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('1 emitida.'));
    expect(body).toEqual({ registrationIds: [55] });
    expect(screen.getByRole('button', { name: 'Emitir constancias' })).toBeDisabled();
  });

  it('reports the students omitted by the batch with their reason', async () => {
    server.use(
      http.get(CANDIDATES_URL, () =>
        HttpResponse.json([candidate({ registrationId: 60 }), candidate({ registrationId: 61, studentName: 'Sofía Luna' })]),
      ),
      http.post(BATCH_URL, () =>
        HttpResponse.json(
          examCertificateBatch({
            issuedCount: 1,
            skippedCount: 1,
            items: [
              { registrationId: 60, studentName: 'Juan Pérez', outcome: 'ISSUED', certificateId: 70 },
              { registrationId: 61, studentName: 'Sofía Luna', outcome: 'NOT_REGISTERED', certificateId: null },
            ],
          }),
        ),
      ),
    );
    const user = userEvent.setup();
    renderSection();

    await user.click(await screen.findByRole('checkbox', { name: 'Seleccionar todos los elegibles (2)' }));
    await user.click(screen.getByRole('button', { name: 'Emitir constancias (2)' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Emitir' }));

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('1 emitida, 1 omitida.'));
    expect(screen.getByRole('status')).toHaveTextContent('Sofía Luna: ya no está inscripto y no se emitió.');
  });

  it('shows that nothing was issued on a total failure and keeps the selection', async () => {
    server.use(http.post(BATCH_URL, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })));
    const user = userEvent.setup();
    renderSection();

    await user.click(await screen.findByRole('checkbox', { name: 'Seleccionar a Ana Paz' }));
    await user.click(screen.getByRole('button', { name: 'Emitir constancias (1)' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Emitir' }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('No se emitió nada.'));
    expect(screen.getByRole('alert')).toHaveTextContent('Podés reintentar con la misma selección.');
    expect(screen.getByRole('checkbox', { name: 'Seleccionar a Ana Paz' })).toBeChecked();
    expect(screen.getByRole('button', { name: 'Emitir constancias (1)' })).toBeEnabled();
  });

  it('shows the pending backend operation message when the batch is not available yet', async () => {
    server.use(http.post(BATCH_URL, () => HttpResponse.json(errorBody('DOCUMENT_OPERATION_PENDING'), { status: 422 })));
    const user = userEvent.setup();
    renderSection();

    await user.click(await screen.findByRole('checkbox', { name: 'Seleccionar a Ana Paz' }));
    await user.click(screen.getByRole('button', { name: 'Emitir constancias (1)' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Emitir' }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Esta operación de documentos todavía no está disponible.'));
  });

  it('lets the director revoke with a mandatory reason and makes the student eligible again', async () => {
    let state = 'ALREADY_ISSUED';
    let revokeBody;
    server.use(
      http.get(CANDIDATES_URL, () =>
        HttpResponse.json([candidate({ registrationId: 56, studentName: 'Luis Gómez', availability: state, certificateId: state === 'ALREADY_ISSUED' ? 12 : null })]),
      ),
      http.post(`${API}/exam-certificates/12/revoke`, async ({ request }) => {
        revokeBody = await request.json();
        state = 'AVAILABLE';
        return HttpResponse.json(examCertificate({ id: 12, status: 'REVOKED' }));
      }),
    );
    const user = userEvent.setup();
    renderSection('director');

    await user.click(await screen.findByRole('button', { name: 'Revocar constancia de Luis Gómez' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Revocar constancia' }));
    expect(await within(dialog).findByText('El motivo es obligatorio.')).toBeInTheDocument();
    expect(revokeBody).toBeUndefined();

    await user.type(within(dialog).getByPlaceholderText('Motivo'), 'Error en el DNI');
    await user.click(within(dialog).getByRole('button', { name: 'Revocar constancia' }));

    expect(await screen.findByText('Constancia revocada.')).toBeInTheDocument();
    expect(revokeBody).toEqual({ reason: 'Error en el DNI' });
    expect(await screen.findByRole('checkbox', { name: 'Seleccionar a Luis Gómez' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Revocar constancia de/ })).not.toBeInTheDocument();

    let issueBody;
    server.use(
      http.post(BATCH_URL, async ({ request }) => {
        issueBody = await request.json();
        return HttpResponse.json(
          examCertificateBatch({
            issuedCount: 1,
            skippedCount: 0,
            items: [{ registrationId: 56, studentName: 'Luis Gómez', outcome: 'ISSUED', certificateId: 90 }],
          }),
        );
      }),
    );
    await user.click(screen.getByRole('checkbox', { name: 'Seleccionar a Luis Gómez' }));
    await user.click(screen.getByRole('button', { name: 'Emitir constancias (1)' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Emitir' }));

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('1 emitida.'));
    expect(issueBody).toEqual({ registrationIds: [56] });
  });

  it('shows the revoke error inside the dialog and keeps it open', async () => {
    server.use(http.post(`${API}/exam-certificates/12/revoke`, () => HttpResponse.json(errorBody('EXAM_CERTIFICATE_NOT_REVOCABLE'), { status: 409 })));
    const user = userEvent.setup();
    renderSection('director');

    await user.click(await screen.findByRole('button', { name: 'Revocar constancia de Luis Gómez' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByPlaceholderText('Motivo'), 'Error');
    await user.click(within(dialog).getByRole('button', { name: 'Revocar constancia' }));

    expect(await within(dialog).findByText('La constancia de examen ya no se puede revocar.')).toBeInTheDocument();
  });

  it.each(['docente', 'administrativo'])('hides the revoke action from the %s', async (role) => {
    renderSection(role);

    expect(await screen.findByText('Emitida')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Revocar constancia de/ })).not.toBeInTheDocument();
  });

  it('shows the not found state when the board belongs to another teacher', async () => {
    server.use(http.get(CANDIDATES_URL, () => HttpResponse.json(errorBody('EXAM_BOARD_NOT_FOUND'), { status: 404 })));
    renderSection();

    expect(await screen.findByText('No encontramos esta mesa')).toBeInTheDocument();
    expect(screen.getByText('La mesa no existe o no es una de las tuyas.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Emitir constancias/ })).not.toBeInTheDocument();
  });

  it('offers to go back to the boards when the board is not found', async () => {
    server.use(http.get(CANDIDATES_URL, () => HttpResponse.json(errorBody('EXAM_BOARD_NOT_FOUND'), { status: 404 })));
    const onBack = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<ExamCertificatesSection mesaId={9} role="docente" onBack={onBack} />);

    await user.click(await screen.findByRole('button', { name: 'Ir a Mis mesas' }));

    expect(onBack).toHaveBeenCalled();
  });

  it('shows the loading state while the candidates are fetched', async () => {
    server.use(http.get(CANDIDATES_URL, () => new Promise(() => {})));
    renderSection();

    expect(await screen.findByLabelText('Cargando constancias')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Emitir constancias/ })).not.toBeInTheDocument();
  });

  it('shows the empty state when nobody was present', async () => {
    server.use(http.get(CANDIDATES_URL, () => HttpResponse.json([])));
    renderSection();

    expect(await screen.findByText('Todavía no hay presentes')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Emitir constancias/ })).not.toBeInTheDocument();
  });

  it('shows a clear load error with a retry that recovers', async () => {
    let fail = true;
    server.use(
      http.get(CANDIDATES_URL, () => (fail ? HttpResponse.json(errorBody('DOCUMENT_OPERATION_PENDING'), { status: 422 }) : HttpResponse.json(examCertificateCandidates()))),
    );
    const user = userEvent.setup();
    renderSection();

    expect(await screen.findByText('No pudimos verificar quién puede recibir constancia. No se puede emitir por ahora.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Emitir constancias/ })).not.toBeInTheDocument();
    fail = false;
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('checkbox', { name: 'Seleccionar a Ana Paz' })).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = renderSection('director');

    await screen.findByRole('checkbox', { name: 'Seleccionar a Ana Paz' });

    expect(await axe(container)).toHaveNoViolations();
  });
});
