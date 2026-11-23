import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { certificateException, certificateExceptionSummary, regularCertificateRequest, regularCertificateRequestList } from '@/test/fixtures/constancias';
import { DirectorExceptionsCard } from '@/features/graduation/certificate-exceptions/components/DirectorExceptionsCard';
import { DirectorCertificateExceptions } from '@/features/graduation/certificate-exceptions/pages/DirectorCertificateExceptions';

const API = 'http://localhost:8080';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { id: 3, roles: ['DIRECTOR'] } }),
}));

const PENDING = [
  certificateException({ id: 9, enrollmentId: 77, organismKind: 'CONSUMER_DEFENSE', organismReference: 'Exp. DC-1' }),
  certificateException({ id: 10, enrollmentId: 78, organismKind: 'COURT', organismReference: 'Causa 2' }),
  certificateException({ id: 11, enrollmentId: 79 }),
  certificateException({ id: 12, enrollmentId: 80 }),
];

function mockData({ pending = PENDING, all = pending } = {}) {
  server.use(
    http.get(`${API}/certificate-exceptions`, ({ request }) =>
      HttpResponse.json(new URL(request.url).searchParams.get('status') === 'PENDING' ? pending : all),
    ),
    http.get(`${API}/certificate-exceptions/summary`, () => HttpResponse.json(certificateExceptionSummary({ pending: pending.length }))),
    http.get(`${API}/regular-certificate-requests`, () => HttpResponse.json(regularCertificateRequestList())),
  );
}

describe('DirectorExceptionsCard', () => {
  it('showsTheKpiTheLinkAndAtMostThreeRows', async () => {
    mockData();
    renderWithProviders(<DirectorExceptionsCard />, { route: '/director/aprobaciones' });

    expect(await screen.findByRole('link', { name: /Ver los 4 pendientes/ })).toBeInTheDocument();
    expect(screen.getByLabelText('4 pendientes')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /^Aprobar excepción de/ })).toHaveLength(3);
    expect(screen.getByText('Defensa del Consumidor · Exp. DC-1')).toBeInTheDocument();
    expect(screen.getAllByText(/2 cuotas vencidas/).length).toBeGreaterThan(0);
  });

  it('usesTheSingularLinkLabelForOnePending', async () => {
    mockData({ pending: [PENDING[0]] });
    renderWithProviders(<DirectorExceptionsCard />, { route: '/director/aprobaciones' });
    expect(await screen.findByRole('link', { name: /Ver el pendiente/ })).toBeInTheDocument();
  });

  it('showsTheEmptyState', async () => {
    mockData({ pending: [] });
    renderWithProviders(<DirectorExceptionsCard />, { route: '/director/aprobaciones' });
    expect(await screen.findByText('Ninguna excepción esperando tu decisión')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Ver todo/ })).toBeInTheDocument();
  });

  it('showsLoadingSkeletonsFirst', () => {
    mockData();
    renderWithProviders(<DirectorExceptionsCard />, { route: '/director/aprobaciones' });
    expect(screen.getByLabelText('Cargando excepciones')).toBeInTheDocument();
  });

  it('showsARetryableSectionErrorWithoutBreakingTheRest', async () => {
    const user = userEvent.setup({ delay: null });
    mockData();
    server.use(http.get(`${API}/certificate-exceptions`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })));
    renderWithProviders(<DirectorExceptionsCard />, { route: '/director/aprobaciones' });

    expect(await screen.findByText('No pudimos cargar esta bandeja')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Crear excepción' })).toBeEnabled();
    server.use(http.get(`${API}/certificate-exceptions`, () => HttpResponse.json(PENDING)));
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('link', { name: /Ver los 4 pendientes/ })).toBeInTheDocument();
  });

  it('requiresAtLeastTwentyCharactersToApproveAndSendsTheReason', async () => {
    const user = userEvent.setup({ delay: null });
    mockData();
    let sent;
    server.use(
      http.post(`${API}/certificate-exceptions/:id/approve`, async ({ request, params }) => {
        sent = { id: params.id, ...(await request.json()) };
        return HttpResponse.json(certificateException({ id: Number(params.id), status: 'APPROVED', validUntil: '2026-11-30T09:00:00' }));
      }),
    );
    renderWithProviders(<DirectorExceptionsCard />, { route: '/director/aprobaciones' });

    await user.click((await screen.findAllByRole('button', { name: /^Aprobar excepción de/ }))[0]);
    const dialog = await screen.findByRole('dialog', { name: 'Aprobar excepción' });
    expect(dialog).toHaveTextContent(/va a poder pedir su constancia aunque tenga deuda\. Vale 7 días y se usa una sola vez\./);
    const submit = within(dialog).getByRole('button', { name: 'Aprobar excepción' });
    await user.type(within(dialog).getByLabelText('Motivo'), 'corto');
    expect(submit).toBeDisabled();
    expect(within(dialog).getByText('El motivo debe tener al menos 20 caracteres.')).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText('Motivo'), ' con detalle suficiente');
    await user.click(submit);

    await waitFor(() => expect(sent).toEqual({ id: '9', reason: 'corto con detalle suficiente' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('rejectsWithAReasonAndWarnsTheRequesterSeesItInTheBell', async () => {
    const user = userEvent.setup({ delay: null });
    mockData();
    let sent;
    server.use(
      http.post(`${API}/certificate-exceptions/:id/reject`, async ({ request, params }) => {
        sent = await request.json();
        return HttpResponse.json(certificateException({ id: Number(params.id), status: 'REJECTED', decisionReason: sent.reason }));
      }),
    );
    renderWithProviders(<DirectorExceptionsCard />, { route: '/director/aprobaciones' });

    await user.click((await screen.findAllByRole('button', { name: /^Rechazar excepción de/ }))[0]);
    const dialog = await screen.findByRole('dialog', { name: 'Rechazar excepción' });
    expect(dialog).toHaveTextContent('Administración va a ver este motivo en la campanita.');
    await user.type(within(dialog).getByLabelText('Motivo'), 'Falta la copia del expediente');
    await user.click(within(dialog).getByRole('button', { name: 'Rechazar excepción' }));

    await waitFor(() => expect(sent).toEqual({ reason: 'Falta la copia del expediente' }));
  });

  it('createsADirectExceptionFromTheStudentsWithDebtAndHandlesConflict', async () => {
    const user = userEvent.setup({ delay: null });
    mockData({ pending: [] });
    server.use(
      http.get(`${API}/regular-certificate-requests`, () =>
        HttpResponse.json([
          regularCertificateRequest({ studentName: 'Lucía Fernández' }),
          regularCertificateRequest({ id: 302, studentName: 'Sin deuda', enrollmentId: 78, hasDebt: false }),
          regularCertificateRequest({ id: 303, studentName: 'Ya abierta', enrollmentId: 79, exceptionStatus: 'PENDING' }),
        ]),
      ),
      http.post(`${API}/certificate-exceptions/direct`, () => HttpResponse.json(errorBody('CERTIFICATE_EXCEPTION_ALREADY_OPEN'), { status: 409 })),
    );
    renderWithProviders(<DirectorExceptionsCard />, { route: '/director/aprobaciones' });

    await user.click(await screen.findByRole('button', { name: 'Crear excepción' }));
    const dialog = await screen.findByRole('dialog', { name: 'Crear excepción' });
    expect(dialog).toHaveTextContent('Nace aprobada, sin pedir autorización.');
    const options = within(await within(dialog).findByLabelText('Alumno con deuda')).getAllByRole('option');
    expect(options.map((option) => option.textContent)).toEqual(['Elegí un alumno', expect.stringMatching(/^Lucía Fernández · .*45\.000,00/)]);
    const submit = within(dialog).getByRole('button', { name: 'Crear y aprobar' });
    expect(submit).toBeDisabled();
    expect(within(dialog).getByLabelText('Alumno con deuda')).toBeRequired();
    await user.click(within(dialog).getByLabelText('Alumno con deuda'));
    await user.tab();
    expect(within(dialog).getByText('Elegí el alumno.')).toBeInTheDocument();

    await user.selectOptions(within(dialog).getByLabelText('Alumno con deuda'), '77:PROGRAM');
    await user.selectOptions(within(dialog).getByLabelText('Tipo de organismo'), 'COURT');
    await user.type(within(dialog).getByLabelText('Expediente o referencia'), 'Causa 77/2026');
    await user.type(within(dialog).getByLabelText('Motivo'), 'Reclamo judicial presentado por el alumno');
    await user.click(submit);

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Ya hay una excepción abierta para este alumno. Revisá la bandeja antes de crear otra.',
    );
  });
});

describe('DirectorCertificateExceptions', () => {
  it('splitsPendingAndHistoryTabs', async () => {
    const user = userEvent.setup({ delay: null });
    mockData({
      all: [
        ...PENDING.slice(0, 2),
        certificateException({ id: 20, enrollmentId: 81, status: 'REJECTED', decidedAt: '2026-11-22T10:00:00', decisionReason: 'Sin respaldo' }),
      ],
    });
    renderWithProviders(<DirectorCertificateExceptions />, { route: '/director/aprobaciones/excepciones-por-reclamo' });

    const pendingTab = await screen.findByRole('tab', { name: /Pendientes/ });
    await waitFor(() => expect(pendingTab).toHaveTextContent('2'));
    expect(screen.getAllByRole('button', { name: /^Aprobar excepción de/ })).toHaveLength(2);
    await user.click(screen.getByRole('tab', { name: 'Historial' }));
    expect(await screen.findByText('Rechazada')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Crear excepción' })).toBeInTheDocument();
  });
});
