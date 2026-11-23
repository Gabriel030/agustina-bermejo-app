import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { certificateException, regularCertificateRequest, regularCertificateRequestList } from '@/test/fixtures/constancias';
import { StaffRegularCertificates } from '@/features/graduation/certificates/pages/StaffRegularCertificates';

const API = 'http://localhost:8080';
const auth = vi.hoisted(() => ({ roles: ['ADMINISTRATIVO'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { id: 5, roles: auth.roles } }),
}));

function mockRequests(requests = regularCertificateRequestList()) {
  server.use(
    http.get(`${API}/regular-certificate-requests`, () => HttpResponse.json(requests)),
    http.get(`${API}/regular-certificates`, () => HttpResponse.json([])),
  );
}

function renderScreen() {
  return renderWithProviders(<StaffRegularCertificates />, { route: '/administrativo/constancias' });
}

function rowOf(name) {
  return screen.getByText(name).closest('tr');
}

describe('StaffRegularCertificates exceptions', () => {
  it('showsEachExceptionStateOnTheDebtRows', async () => {
    mockRequests([
      regularCertificateRequest({ id: 1, studentName: 'Alumna Sin', enrollmentId: 1 }),
      regularCertificateRequest({ id: 2, studentName: 'Alumna Pendiente', enrollmentId: 2, exceptionId: 9, exceptionStatus: 'PENDING' }),
      regularCertificateRequest({ id: 3, studentName: 'Alumna Aprobada', enrollmentId: 3, exceptionId: 10, exceptionStatus: 'APPROVED', exceptionValidUntil: '2026-11-30T10:00:00' }),
      regularCertificateRequest({ id: 4, studentName: 'Alumna Rechazada', enrollmentId: 4, exceptionId: 11, exceptionStatus: 'REJECTED' }),
    ]);
    renderScreen();

    const without = within(await screen.findByText('Alumna Sin').then((node) => node.closest('tr')));
    expect(without.getByText('Con deuda')).toBeInTheDocument();
    expect(without.getByText(/45\.000,00/)).toBeInTheDocument();
    expect(without.getByText('2 cuotas vencidas')).toBeInTheDocument();
    expect(without.getByRole('button', { name: /Pedir excepción para Alumna Sin/ })).toBeInTheDocument();

    expect(within(rowOf('Alumna Pendiente')).getByText('Excepción pendiente')).toBeInTheDocument();
    const approved = within(rowOf('Alumna Aprobada'));
    expect(approved.getByText('Vence 30/11/2026')).toBeInTheDocument();
    expect(approved.getByRole('button', { name: /Emitir con excepción/ })).toBeInTheDocument();
    const rejected = within(rowOf('Alumna Rechazada'));
    expect(rejected.getByText('Excepción rechazada')).toBeInTheDocument();
    expect(rejected.getByRole('button', { name: /Ver motivo/ })).toBeInTheDocument();
  });

  it('validatesTheRequestFormAndSendsItToTheDirector', async () => {
    const user = userEvent.setup({ delay: null });
    mockRequests([regularCertificateRequest({ studentName: 'Lucía Fernández' })]);
    let sent;
    server.use(
      http.post(`${API}/certificate-exceptions`, async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json(certificateException({ id: 40 }), { status: 201 });
      }),
    );
    renderScreen();

    await user.click(await screen.findByRole('button', { name: /Pedir excepción para Lucía/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Pedir excepción' });
    const submit = within(dialog).getByRole('button', { name: 'Enviar al Director' });
    expect(submit).toBeDisabled();
    expect(within(dialog).getByTestId('student-debt-context')).toHaveTextContent('Tecnicatura en Enfermería');

    await user.click(within(dialog).getByLabelText('Tipo de organismo'));
    await user.tab();
    expect(within(dialog).getByText('Elegí el tipo de organismo.')).toBeInTheDocument();
    await user.click(within(dialog).getByLabelText('Expediente o referencia'));
    await user.tab();
    expect(within(dialog).getByText('Cargá el número de expediente.')).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText('Motivo'), 'muy corto');
    expect(within(dialog).getByText('El motivo debe tener al menos 20 caracteres.')).toBeInTheDocument();
    expect(submit).toBeDisabled();

    await user.selectOptions(within(dialog).getByLabelText('Tipo de organismo'), 'CONSUMER_DEFENSE');
    await user.type(within(dialog).getByLabelText('Expediente o referencia'), 'Exp. DC-2026-48213');
    await user.type(within(dialog).getByLabelText('Motivo'), ' y ahora con suficiente detalle del reclamo');
    expect(submit).toBeEnabled();
    await user.click(submit);

    await waitFor(() => expect(sent).toMatchObject({
      enrollmentId: 77,
      kind: 'PROGRAM',
      organismKind: 'CONSUMER_DEFENSE',
      organismReference: 'Exp. DC-2026-48213',
    }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Pedir excepción' })).not.toBeInTheDocument());
  });

  it('showsTheAlreadyOpenBannerOnConflict', async () => {
    const user = userEvent.setup({ delay: null });
    mockRequests([regularCertificateRequest({ studentName: 'Lucía Fernández' })]);
    server.use(
      http.post(`${API}/certificate-exceptions`, () => HttpResponse.json(errorBody('CERTIFICATE_EXCEPTION_ALREADY_OPEN'), { status: 409 })),
    );
    renderScreen();

    await user.click(await screen.findByRole('button', { name: /Pedir excepción para Lucía/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Pedir excepción' });
    await user.selectOptions(within(dialog).getByLabelText('Tipo de organismo'), 'COURT');
    await user.type(within(dialog).getByLabelText('Expediente o referencia'), 'Causa 77/2026');
    await user.type(within(dialog).getByLabelText('Motivo'), 'El alumno presentó un reclamo formal');
    await user.click(within(dialog).getByRole('button', { name: 'Enviar al Director' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Ya hay un pedido abierto para esta inscripción. Revisá Excepciones antes de pedir otra.',
    );
  });

  it('emitsWithTheApprovedExceptionId', async () => {
    const user = userEvent.setup({ delay: null });
    mockRequests([
      regularCertificateRequest({ studentName: 'Alumna Aprobada', exceptionId: 10, exceptionStatus: 'APPROVED', exceptionValidUntil: '2026-11-30T10:00:00' }),
    ]);
    let sent;
    server.use(
      http.post(`${API}/regular-certificates`, async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({
          id: 31,
          code: '7QK3M9XH2VBN4T8RGJDA1B2C3X',
          studentId: 41,
          studentName: 'Alumna Aprobada',
          programName: 'Tecnicatura en Enfermería',
          kind: 'PROGRAM',
          enrollmentId: 77,
          status: 'VALID',
          issuedAt: '2026-11-23T09:00:00',
          expiresAt: '2026-12-23T23:59:59',
          standingBasis: 'MANUALLY_CONFIRMED',
          billingModeAtIssue: 'OFF',
          verificationUrl: 'http://localhost:5173/verificar/7QK3M9XH2VBN4T8RGJDA1B2C3X',
        }, { status: 201 });
      }),
    );
    renderScreen();

    await user.click(await screen.findByRole('button', { name: /Emitir con excepción la constancia de Alumna Aprobada/ }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Emitir con excepción' }));

    await waitFor(() => expect(sent).toMatchObject({ enrollmentId: 77, exceptionId: 10 }));
  });

  it('listsOwnExceptionsWithPendingCountAndStatusPill', async () => {
    const user = userEvent.setup({ delay: null });
    mockRequests();
    server.use(
      http.get(`${API}/certificate-exceptions`, () =>
        HttpResponse.json([
          certificateException({ id: 9, requestedById: 5, enrollmentId: 77, organismKind: 'COURT', organismReference: 'Causa 77/2026' }),
          certificateException({ id: 10, requestedById: 5, enrollmentId: 81, organismReference: 'Exp. 123', status: 'APPROVED', validUntil: '2026-11-30T10:00:00' }),
          certificateException({ id: 11, requestedById: 99, enrollmentId: 78 }),
        ]),
      ),
    );
    renderScreen();

    const tab = await screen.findByRole('tab', { name: /Excepciones/ });
    await waitFor(() => expect(tab).toHaveTextContent('1'));
    await user.click(tab);
    expect(await screen.findByText('Causa 77/2026')).toBeInTheDocument();
    expect(screen.getByText('Aprobada · Vence 30/11')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /Lucía Fernández|Valeria Núñez/ })).toHaveLength(2);
  });

  it('showsTheEmptyStateWithAWayBackToTheRequests', async () => {
    const user = userEvent.setup({ delay: null });
    mockRequests([]);
    server.use(http.get(`${API}/certificate-exceptions`, () => HttpResponse.json([])));
    renderScreen();

    await user.click(await screen.findByRole('tab', { name: /Excepciones/ }));
    expect(await screen.findByText('Todavía no pediste ninguna excepción')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Ir a los pedidos' }));
    expect(screen.getByRole('tab', { name: /Pedidos/ })).toHaveAttribute('data-state', 'active');
  });

  it('hidesTheExceptionsTabAndRequestButtonFromTheDirector', async () => {
    auth.roles = ['DIRECTOR'];
    mockRequests([regularCertificateRequest({ studentName: 'Lucía Fernández' })]);
    renderScreen();

    expect(await screen.findByText('Lucía Fernández')).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: /Excepciones/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Pedir excepción/ })).not.toBeInTheDocument();
    auth.roles = ['ADMINISTRATIVO'];
  });
});
