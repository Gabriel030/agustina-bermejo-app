import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { StaffRegularCertificates } from '@/features/graduation/certificates/pages/StaffRegularCertificates';

const API = 'http://localhost:8080';
const auth = vi.hoisted(() => ({ roles: ['DIRECTOR'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { roles: auth.roles } }),
}));

function request(overrides = {}) {
  return {
    id: 71,
    studentId: 9,
    studentName: 'Valentina Suárez',
    programName: 'Tecnicatura en Administración',
    kind: 'PROGRAM',
    enrollmentId: 501,
    channel: 'STUDENT_PORTAL',
    outcome: 'PENDING_STAFF',
    reasons: [],
    requestedAt: '2026-11-12T10:00:00',
    certificateId: 0,
    rejectionReason: '',
    ...overrides,
  };
}

function certificate(overrides = {}) {
  return {
    id: 31,
    code: '7QK3M9XH2VBN4T8RGJDA1B2C3X',
    studentId: 9,
    studentName: 'Valentina Suárez',
    programName: 'Tecnicatura en Administración',
    kind: 'PROGRAM',
    enrollmentId: 501,
    status: 'VALID',
    issuedAt: '2026-11-04T09:00:00',
    expiresAt: '2026-12-04T23:59:59',
    standingBasis: 'MANUALLY_CONFIRMED',
    billingModeAtIssue: 'OFF',
    verificationUrl: 'http://localhost:5173/verificar/7QK3M9XH2VBN4T8RGJDA1B2C3X',
    ...overrides,
  };
}

function mockLists({ requests = [request()], certificates = [certificate()] } = {}) {
  server.use(
    http.get(`${API}/regular-certificate-requests`, () => HttpResponse.json(requests)),
    http.get(`${API}/regular-certificates`, () => HttpResponse.json(certificates)),
  );
}

function renderScreen() {
  return renderWithProviders(<StaffRegularCertificates />, { route: '/director/constancias' });
}

async function openRowMenu(name) {
  await userEvent.click(await screen.findByRole('button', { name }));
}

beforeEach(() => {
  auth.roles = ['DIRECTOR'];
});

describe('StaffRegularCertificates', () => {
  it('lists pending requests with student and program and the count in the tab', async () => {
    mockLists({ requests: [request(), request({ id: 72, studentId: 10, studentName: 'Tomás Herrera', programName: 'Tecnicatura en Seguridad e Higiene', enrollmentId: 502, requestedAt: '2026-11-10T10:00:00' })] });
    renderScreen();

    const rows = await screen.findAllByRole('row');
    await waitFor(() => expect(within(screen.getAllByRole('row')[1]).getByText('Tomás Herrera')).toBeInTheDocument());
    expect(rows.length).toBe(3);
    expect(screen.getByText('Valentina Suárez')).toBeInTheDocument();
    expect(screen.getByText('Tecnicatura en Administración')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Pedidos/ })).toHaveTextContent('2');
  });

  it('shows a single primary button per pending row', async () => {
    mockLists();
    renderScreen();

    await screen.findByText('Valentina Suárez');
    expect(screen.getAllByRole('button', { name: /Confirmar y emitir/ })).toHaveLength(1);
    expect(screen.queryByRole('button', { name: /^Rechazar/ })).not.toBeInTheDocument();
  });

  it('issues confirming the standing with the optional note', async () => {
    mockLists();
    let body;
    server.use(
      http.post(`${API}/regular-certificates`, async ({ request: req }) => {
        body = await req.json();
        return HttpResponse.json(certificate(), { status: 201 });
      }),
    );
    renderScreen();

    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar y emitir la constancia de Valentina Suárez' }));
    await userEvent.type(await screen.findByRole('textbox'), 'Comprobante de octubre revisado');
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar y emitir' }));

    await waitFor(() =>
      expect(body).toEqual({
        kind: 'PROGRAM',
        enrollmentId: 501,
        standingConfirmed: true,
        standingEvidence: 'Comprobante de octubre revisado',
      }),
    );
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('issues without a note when the field is left empty', async () => {
    mockLists();
    let body;
    server.use(
      http.post(`${API}/regular-certificates`, async ({ request: req }) => {
        body = await req.json();
        return HttpResponse.json(certificate(), { status: 201 });
      }),
    );
    renderScreen();

    await userEvent.click(await screen.findByRole('button', { name: /Confirmar y emitir la constancia/ }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirmar y emitir' }));

    await waitFor(() => expect(body).toEqual({ kind: 'PROGRAM', enrollmentId: 501, standingConfirmed: true, standingEvidence: '' }));
  });

  it('keeps the dialog open and keeps the row when issuing fails', async () => {
    mockLists();
    server.use(
      http.post(`${API}/regular-certificates`, () => HttpResponse.json(errorBody('REGULAR_CERTIFICATE_ALREADY_ISSUED'), { status: 409 })),
    );
    renderScreen();

    await userEvent.click(await screen.findByRole('button', { name: /Confirmar y emitir la constancia/ }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirmar y emitir' }));

    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());
  });

  it('requires at least 10 characters to reject and sends the reason', async () => {
    mockLists();
    let body;
    server.use(
      http.post(`${API}/regular-certificate-requests/71/reject`, async ({ request: req }) => {
        body = await req.json();
        return HttpResponse.json(request({ outcome: 'REJECTED', rejectionReason: body.reason }));
      }),
    );
    renderScreen();

    await openRowMenu('Más acciones de Valentina Suárez');
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Rechazar' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByRole('textbox'), 'corto');
    expect(within(dialog).getByText(/mínimo 10/)).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Rechazar pedido' })).toBeDisabled();
    expect(body).toBeUndefined();

    await userEvent.type(within(dialog).getByRole('textbox'), ' ya no alcanza el pago de octubre');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Rechazar pedido' }));

    await waitFor(() => expect(body).toEqual({ reason: 'corto ya no alcanza el pago de octubre' }));
  });

  it('links the expanded summary to the student attendance and payments', async () => {
    mockLists();
    renderScreen();

    await screen.findByText('Valentina Suárez');
    await userEvent.click(screen.getByRole('button', { name: /Ver resumen/ }));

    expect(await screen.findByRole('link', { name: /Ver asistencia/ })).toHaveAttribute('href', '/director/alumnos/9?view=academico');
    expect(screen.getByRole('link', { name: /Ver pagos/ })).toHaveAttribute('href', '/director/alumnos/9?view=financiero');
  });

  it('lists issued certificates with their status and expiry', async () => {
    mockLists({
      certificates: [certificate(), certificate({ id: 32, studentId: 10, studentName: 'Tomás Herrera', programName: 'Tecnicatura en Seguridad e Higiene', enrollmentId: 502, status: 'REVOKED', issuedAt: '2026-09-22T09:00:00' })],
    });
    renderScreen();

    await userEvent.click(screen.getByRole('tab', { name: 'Emitidas' }));

    expect(await screen.findByText('Vigente')).toBeInTheDocument();
    expect(screen.getByText('Revocada')).toBeInTheDocument();
    expect(await screen.findByText('Tomás Herrera')).toBeInTheDocument();
  });

  it('lets the director revoke a valid certificate with a reason', async () => {
    mockLists({ certificates: [certificate(), certificate({ id: 32, studentId: 10, studentName: 'Tomás Herrera', programName: 'Tecnicatura en Seguridad e Higiene', enrollmentId: 502, status: 'EXPIRED' })] });
    let body;
    server.use(
      http.post(`${API}/regular-certificates/31/revoke`, async ({ request: req }) => {
        body = await req.json();
        return HttpResponse.json(certificate({ status: 'REVOKED' }));
      }),
    );
    renderScreen();

    await userEvent.click(screen.getByRole('tab', { name: 'Emitidas' }));
    await openRowMenu('Acciones de Valentina Suárez');
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Revocar' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('No se puede deshacer');
    await userEvent.type(within(dialog).getByRole('textbox'), 'Emitida por error de legajo');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Revocar' }));

    await waitFor(() => expect(body).toEqual({ reason: 'Emitida por error de legajo' }));
  });

  it('offers revoke only on valid certificates', async () => {
    mockLists({ certificates: [certificate({ status: 'EXPIRED' })] });
    renderScreen();

    await userEvent.click(screen.getByRole('tab', { name: 'Emitidas' }));
    await openRowMenu('Acciones de Valentina Suárez');

    expect(await screen.findByRole('menuitem', { name: 'Descargar PDF' })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Revocar' })).not.toBeInTheDocument();
  });

  it('hides revoke from the administrative role', async () => {
    auth.roles = ['ADMINISTRATIVO'];
    mockLists();
    renderScreen();

    await userEvent.click(screen.getByRole('tab', { name: 'Emitidas' }));
    await openRowMenu('Acciones de Valentina Suárez');

    expect(await screen.findByRole('menuitem', { name: 'Copiar enlace de verificación' })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Revocar' })).not.toBeInTheDocument();
  });

  it('shows the empty states', async () => {
    mockLists({ requests: [], certificates: [] });
    renderScreen();

    expect(await screen.findByText('No hay pedidos pendientes')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('tab', { name: 'Emitidas' }));
    expect(await screen.findByText('Todavía no se emitió ninguna constancia')).toBeInTheDocument();
  });

  it('shows a retryable error when the pending list fails', async () => {
      server.use(http.get(`${API}/regular-certificate-requests`, () => new HttpResponse(null, { status: 500 })));
    renderScreen();

    expect(await screen.findByText('No pudimos cargar los pedidos')).toBeInTheDocument();
  });
});
