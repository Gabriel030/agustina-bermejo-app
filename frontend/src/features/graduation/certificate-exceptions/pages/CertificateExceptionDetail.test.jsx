import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { certificateException, regularCertificateRequest } from '@/test/fixtures/constancias';
import { CertificateExceptionDetail } from '@/features/graduation/certificate-exceptions/pages/CertificateExceptionDetail';

const API = 'http://localhost:8080';
const auth = vi.hoisted(() => ({ roles: ['DIRECTOR'], id: 3 }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { id: auth.id, roles: auth.roles } }),
}));

function renderDetail(exception, { director = true } = {}) {
  auth.roles = director ? ['DIRECTOR'] : ['ADMINISTRATIVO'];
  auth.id = director ? 3 : 5;
  server.use(
    http.get(`${API}/certificate-exceptions/:id`, () =>
      exception instanceof Response ? exception : HttpResponse.json(exception),
    ),
    http.get(`${API}/regular-certificate-requests`, () =>
      HttpResponse.json([regularCertificateRequest({ studentName: 'Tomás Acosta', enrollmentId: 80 })]),
    ),
  );
  return renderWithProviders(
    <Routes>
      <Route path="/:role/detalle/:id" element={<CertificateExceptionDetail />} />
    </Routes>,
    { route: `/${director ? 'director' : 'administrativo'}/detalle/9` },
  );
}

describe('CertificateExceptionDetail', () => {
  it('letsTheDirectorDecideAPendingRequestAndShowsTheDebtItWasAskedWith', async () => {
    renderDetail(certificateException());

    expect(await screen.findByRole('heading', { name: 'Tomás Acosta' })).toBeInTheDocument();
    expect(screen.getByText('Pendiente')).toBeInTheDocument();
    expect(screen.getByText(/45\.000,00/)).toBeInTheDocument();
    expect(screen.getByText(/2 cuotas vencidas/)).toBeInTheDocument();
    expect(screen.queryByText('Cumple')).not.toBeInTheDocument();
    expect(screen.getByText('La excepción no reemplaza la asistencia.')).toBeInTheDocument();
    expect(screen.getByText('Pedida por Administración')).toBeInTheDocument();
    const decision = screen.getByRole('heading', { name: 'Tu decisión' }).closest('div');
    expect(within(decision).getByRole('button', { name: 'Aprobar' })).toBeInTheDocument();
    expect(within(decision).getByRole('button', { name: 'Rechazar' })).toBeInTheDocument();
  });

  it('showsTheRejectionReasonInABannerAndTheTimeline', async () => {
    renderDetail(certificateException({ status: 'REJECTED', decidedAt: '2026-11-22T10:00:00', decisionReason: 'Falta el expediente completo' }));

    expect(await screen.findByText('Rechazada.')).toBeInTheDocument();
    expect(screen.getAllByText('Falta el expediente completo')).toHaveLength(2);
    expect(screen.getByText('Rechazada por el Director')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Tu decisión' })).not.toBeInTheDocument();
  });

  it('showsApprovedAndDirectCreatedBanners', async () => {
    renderDetail(certificateException({ status: 'APPROVED', createdByDirector: true, validUntil: '2026-11-30T09:00:00' }));

    expect(await screen.findByText('Aprobada hasta el 30/11/2026.')).toBeInTheDocument();
    expect(screen.getByText('Creada por el Director, sin pedido previo.')).toBeInTheDocument();
    expect(screen.getByText('Creada y aprobada por el Director')).toBeInTheDocument();
  });

  it('showsTheExpiredBannerAndTimeline', async () => {
    renderDetail(certificateException({ status: 'EXPIRED', decidedAt: '2026-11-15T10:00:00', validUntil: '2026-11-20T09:00:00' }));

    expect(await screen.findByText('Venció el 20/11/2026.')).toBeInTheDocument();
    expect(screen.getByText('Venció sin usarse')).toBeInTheDocument();
  });

  it('letsTheOwnerWithdrawAfterConfirming', async () => {
    const user = userEvent.setup({ delay: null });
    let withdrawn = false;
    server.use(
      http.post(`${API}/certificate-exceptions/:id/withdraw`, ({ params }) => {
        withdrawn = true;
        return HttpResponse.json(certificateException({ id: Number(params.id), status: 'WITHDRAWN' }));
      }),
    );
    renderDetail(certificateException({ requestedById: 5 }), { director: false });

    await user.click(await screen.findByRole('button', { name: 'Retirar pedido' }));
    const dialog = await screen.findByRole('alertdialog');
    expect(withdrawn).toBe(false);
    await user.click(within(dialog).getByRole('button', { name: 'Retirar pedido' }));
    await waitFor(() => expect(withdrawn).toBe(true));
  });

  it('hidesWithdrawFromSomeoneWhoDidNotRequestIt', async () => {
    renderDetail(certificateException({ requestedById: 77 }), { director: false });
    expect(await screen.findByRole('heading', { name: 'Tomás Acosta' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Retirar pedido' })).not.toBeInTheDocument();
  });

  it('showsNotFoundFor404', async () => {
    renderDetail(HttpResponse.json(errorBody('CERTIFICATE_EXCEPTION_NOT_FOUND'), { status: 404 }));
    expect(await screen.findByText('No encontramos este pedido')).toBeInTheDocument();
  });

  it('showsNoPermissionFor403', async () => {
    renderDetail(HttpResponse.json(errorBody('FORBIDDEN'), { status: 403 }));
    expect(await screen.findByText('No tenés permiso para ver esta pantalla')).toBeInTheDocument();
  });
});
