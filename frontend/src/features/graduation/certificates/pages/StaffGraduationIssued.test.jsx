import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { axe } from '@/test/axe';
import { errorBody } from '@/test/fixtures/errors';
import { graduationCertificateDetail, graduationCertificateSummary, springPage } from '@/test/fixtures/certificates';
import { revokeNotRevocableHandler } from '@/test/mocks/certificateErrorHandlers';
import { StaffGraduationCertificates } from '@/features/graduation/certificates/pages/StaffGraduationCertificates';

const API = 'http://localhost:8080';
const auth = vi.hoisted(() => ({ roles: ['DIRECTOR'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { roles: auth.roles } }),
}));

const validRow = graduationCertificateSummary({ id: 101, holderFullName: 'Pérez, Camila', status: 'VALID' });
const revokedRow = graduationCertificateSummary({ id: 102, holderFullName: 'Ruiz, Lucía', status: 'REVOKED' });

function issuedList(rows) {
  server.use(http.get(`${API}/graduation-certificates`, () => HttpResponse.json(springPage(rows))));
}

function detailOf(id, overrides) {
  server.use(http.get(`${API}/graduation-certificates/${id}`, () =>
    HttpResponse.json(graduationCertificateDetail({ id, ...overrides })),
  ));
}

async function openIssuedTab(user) {
  await user.click(screen.getByRole('tab', { name: 'Emitidos' }));
}

async function openDetail(user, name) {
  await user.click(await screen.findByRole('button', { name: `Ver detalle de ${name}` }));
  return screen.findByRole('dialog');
}

function renderPage() {
  return renderWithProviders(<StaffGraduationCertificates />);
}

describe('StaffGraduationCertificates · Emitidos', () => {
  beforeEach(() => {
    auth.roles = ['DIRECTOR'];
    server.use(http.get(`${API}/graduation-certificates/eligible`, () => HttpResponse.json(springPage([]))));
    URL.createObjectURL = vi.fn(() => 'blob:pdf');
    URL.revokeObjectURL = vi.fn();
  });

  it('lists issued certificates with their status and a PDF button only for valid ones', async () => {
    const user = userEvent.setup();
    issuedList([validRow, revokedRow]);
    renderPage();
    await openIssuedTab(user);

    expect(await screen.findByText('Pérez, Camila')).toBeInTheDocument();
    expect(screen.getByText('Revocado')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Descargar PDF de Pérez, Camila' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Descargar PDF de Ruiz, Lucía' })).not.toBeInTheDocument();
  });

  it('shows the empty state when there are no issued certificates', async () => {
    const user = userEvent.setup();
    issuedList([]);
    renderPage();
    await openIssuedTab(user);

    expect(await screen.findByText('No hay certificados emitidos para mostrar')).toBeInTheDocument();
  });

  it('shows the error state when the issued list fails to load', async () => {
    const user = userEvent.setup();
    server.use(http.get(`${API}/graduation-certificates`, () => HttpResponse.json(errorBody('INTERNAL'), { status: 500 })));
    renderPage();
    await openIssuedTab(user);

    expect(await screen.findByText('No pudimos cargar los certificados emitidos')).toBeInTheDocument();
  });

  it('shows the loading state while the issued list is pending', async () => {
    const user = userEvent.setup();
    server.use(http.get(`${API}/graduation-certificates`, () => new Promise(() => {})));
    renderPage();
    await openIssuedTab(user);

    expect(await screen.findByLabelText('Cargando')).toBeInTheDocument();
  });

  it('sends the status filter to the backend', async () => {
    const user = userEvent.setup();
    const statuses = [];
    server.use(
      http.get(`${API}/graduation-certificates`, ({ request }) => {
        statuses.push(new URL(request.url).searchParams.get('status'));
        return HttpResponse.json(springPage([validRow]));
      }),
    );
    renderPage();
    await openIssuedTab(user);
    await screen.findByText('Pérez, Camila');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Filtrar por estado' }), 'REVOKED');

    await waitFor(() => expect(statuses).toContain('REVOKED'));
  });

  it('shows the revoked reason and a link to the certificate that replaces it', async () => {
    const user = userEvent.setup();
    issuedList([revokedRow]);
    detailOf(102, {
      status: 'REVOKED',
      holderFullName: 'Ruiz, Lucía',
      revocationReason: 'Error en el nombre del alumno',
      replacedByCertificateId: 150,
    });
    renderPage();
    await openIssuedTab(user);
    const dialog = await openDetail(user, 'Ruiz, Lucía');

    expect(await within(dialog).findByText('Error en el nombre del alumno')).toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Descargar PDF' })).not.toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Ver el certificado que lo reemplaza' })).toBeInTheDocument();
  });

  it('shows which certificate a reissued one replaces', async () => {
    const user = userEvent.setup();
    issuedList([validRow]);
    detailOf(101, { replacesCertificateId: 77 });
    renderPage();
    await openIssuedTab(user);
    const dialog = await openDetail(user, 'Pérez, Camila');

    expect(await within(dialog).findByRole('button', { name: 'Reemplaza al certificado #77' })).toBeInTheDocument();
  });

  it('hides the revoke and reissue actions from the administrative user', async () => {
    const user = userEvent.setup();
    auth.roles = ['ADMINISTRATIVO'];
    issuedList([validRow]);
    renderPage();
    await openIssuedTab(user);
    const dialog = await openDetail(user, 'Pérez, Camila');

    await within(dialog).findByRole('button', { name: 'Descargar PDF' });
    expect(within(dialog).queryByRole('button', { name: 'Revocar' })).not.toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Reemitir' })).not.toBeInTheDocument();
  });

  it('shows the revoke and reissue actions to the director', async () => {
    const user = userEvent.setup();
    issuedList([validRow]);
    renderPage();
    await openIssuedTab(user);
    const dialog = await openDetail(user, 'Pérez, Camila');

    expect(await within(dialog).findByRole('button', { name: 'Revocar' })).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Reemitir' })).toBeInTheDocument();
  });

  it('does not send a reason shorter than 10 characters or made only of spaces', async () => {
    const user = userEvent.setup();
    const sent = vi.fn();
    server.use(http.post(`${API}/graduation-certificates/:id/revoke`, async ({ request }) => {
      sent(await request.json());
      return HttpResponse.json(graduationCertificateDetail({ status: 'REVOKED' }));
    }));
    issuedList([validRow]);
    renderPage();
    await openIssuedTab(user);
    const dialog = await openDetail(user, 'Pérez, Camila');
    await user.click(await within(dialog).findByRole('button', { name: 'Revocar' }));

    const reasonDialog = await screen.findByRole('dialog', { name: 'Revocar certificado' });
    const field = within(reasonDialog).getByLabelText('Motivo de la revocación');
    await user.type(field, '   corto   ');
    expect(within(reasonDialog).getByRole('button', { name: 'Continuar' })).toBeDisabled();

    await user.clear(field);
    await user.type(field, '          ');
    expect(within(reasonDialog).getByRole('button', { name: 'Continuar' })).toBeDisabled();
    expect(sent).not.toHaveBeenCalled();
  });

  it('shows how many characters exceed the 500 limit', async () => {
    const user = userEvent.setup();
    issuedList([validRow]);
    renderPage();
    await openIssuedTab(user);
    const dialog = await openDetail(user, 'Pérez, Camila');
    await user.click(await within(dialog).findByRole('button', { name: 'Revocar' }));

    const reasonDialog = await screen.findByRole('dialog', { name: 'Revocar certificado' });
    await user.click(within(reasonDialog).getByLabelText('Motivo de la revocación'));
    await user.paste('x'.repeat(512));

    expect(within(reasonDialog).getByRole('alert')).toHaveTextContent('Sobran 12 caracteres');
    expect(within(reasonDialog).getByRole('button', { name: 'Continuar' })).toBeDisabled();
  });

  it('revokes after two steps, sends the trimmed reason and shows the result', async () => {
    const user = userEvent.setup();
    let sentBody;
    server.use(http.post(`${API}/graduation-certificates/:id/revoke`, async ({ request }) => {
      sentBody = await request.json();
      return HttpResponse.json(graduationCertificateDetail({ status: 'REVOKED' }));
    }));
    issuedList([validRow]);
    renderPage();
    await openIssuedTab(user);
    const dialog = await openDetail(user, 'Pérez, Camila');
    await user.click(await within(dialog).findByRole('button', { name: 'Revocar' }));

    const reasonDialog = await screen.findByRole('dialog', { name: 'Revocar certificado' });
    await user.type(within(reasonDialog).getByLabelText('Motivo de la revocación'), '  Error en la fecha de egreso  ');
    await user.click(within(reasonDialog).getByRole('button', { name: 'Continuar' }));

    const confirmDialog = await screen.findByRole('dialog', { name: 'Confirmar revocación' });
    await user.click(within(confirmDialog).getByRole('button', { name: 'Revocar certificado' }));

    expect(await screen.findByRole('heading', { name: 'Certificado revocado' })).toBeInTheDocument();
    expect(sentBody).toEqual({ reason: 'Error en la fecha de egreso' });
  });

  it('reissues a certificate and shows the new one in the result', async () => {
    const user = userEvent.setup();
    issuedList([validRow]);
    renderPage();
    await openIssuedTab(user);
    const dialog = await openDetail(user, 'Pérez, Camila');
    await user.click(await within(dialog).findByRole('button', { name: 'Reemitir' }));

    const reasonDialog = await screen.findByRole('dialog', { name: 'Reemitir certificado' });
    await user.type(within(reasonDialog).getByLabelText('Motivo de la reemisión'), 'Cambio en el nombre del título');
    await user.click(within(reasonDialog).getByRole('button', { name: 'Continuar' }));
    const confirmDialog = await screen.findByRole('dialog', { name: 'Confirmar reemisión' });
    await user.click(within(confirmDialog).getByRole('button', { name: 'Reemitir certificado' }));

    expect(await screen.findByRole('heading', { name: 'Certificado reemitido' })).toBeInTheDocument();
  });

  it('shows the domain error when the certificate is no longer revocable', async () => {
    const user = userEvent.setup();
    server.use(revokeNotRevocableHandler);
    issuedList([validRow]);
    renderPage();
    await openIssuedTab(user);
    const dialog = await openDetail(user, 'Pérez, Camila');
    await user.click(await within(dialog).findByRole('button', { name: 'Revocar' }));

    const reasonDialog = await screen.findByRole('dialog', { name: 'Revocar certificado' });
    await user.type(within(reasonDialog).getByLabelText('Motivo de la revocación'), 'Motivo suficientemente largo');
    await user.click(within(reasonDialog).getByRole('button', { name: 'Continuar' }));
    const confirmDialog = await screen.findByRole('dialog', { name: 'Confirmar revocación' });
    await user.click(within(confirmDialog).getByRole('button', { name: 'Revocar certificado' }));

    expect(await within(confirmDialog).findByRole('alert')).toHaveTextContent('Solo se puede revocar o reemitir un certificado vigente.');
  });

  it('has no accessibility violations in the issued list', async () => {
    const user = userEvent.setup();
    issuedList([validRow, revokedRow]);
    const { container } = renderPage();
    await openIssuedTab(user);
    await screen.findByText('Pérez, Camila');

    expect(await axe(container)).toHaveNoViolations();
  });
});
