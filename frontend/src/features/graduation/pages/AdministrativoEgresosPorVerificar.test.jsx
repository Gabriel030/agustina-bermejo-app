import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { axe } from '@/test/axe';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { AdministrativoEgresosPorVerificar } from '@/features/graduation/pages/AdministrativoEgresosPorVerificar';

const API = 'http://localhost:8080';

function graduation(id, name, status, extra = {}) {
  return {
    id,
    programEnrollmentId: id,
    studentId: id,
    studentName: name,
    programName: 'Tecnicatura en Administración',
    status,
    requestedAt: '2026-09-12T10:00:00',
    decidedAt: null,
    rejectionReason: null,
    ...extra,
  };
}

function mockTray({ requested = [], verified = [], approved = [], rejected = [] } = {}) {
  server.use(
    http.get(`${API}/graduation-requests`, ({ request }) => {
      const status = new URL(request.url).searchParams.get('status');
      const byStatus = { REQUESTED: requested, ADMIN_VERIFIED: verified, APPROVED: approved, REJECTED: rejected };
      return HttpResponse.json(byStatus[status] ?? []);
    }),
  );
}

describe('AdministrativoEgresosPorVerificar', () => {
  it('lists requested graduations with the count and links to the detail', async () => {
    mockTray({ requested: [graduation(41, 'Valentina Suárez', 'REQUESTED'), graduation(43, 'Joaquín Benítez', 'REQUESTED')] });
    const { container } = renderWithProviders(<AdministrativoEgresosPorVerificar />, { route: '/administrativo/egresos-por-verificar' });

    expect(await screen.findByRole('tab', { name: 'Por verificar (2)' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Historial' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Valentina Suárez/ })).toHaveAttribute('href', '/administrativo/egresos-por-verificar/41');
    expect(screen.queryByText(/Con deuda/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Aprobaciones/)).not.toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('shows the empty state when nothing is waiting', async () => {
    mockTray();
    renderWithProviders(<AdministrativoEgresosPorVerificar />, { route: '/administrativo/egresos-por-verificar' });

    expect(await screen.findByText('Nada por verificar')).toBeInTheDocument();
  });

  it('shows verified, approved and rejected graduations in the history', async () => {
    mockTray({
      verified: [graduation(38, 'Martín Acosta', 'ADMIN_VERIFIED')],
      approved: [graduation(35, 'Camila Ibarra', 'APPROVED', { decidedAt: '2026-08-28T10:00:00' })],
      rejected: [graduation(33, 'Sofía Peralta', 'REJECTED', { decidedAt: '2026-08-20T10:00:00', rejectionReason: 'Documentación incompleta' })],
    });
    renderWithProviders(<AdministrativoEgresosPorVerificar />, { route: '/administrativo/egresos-por-verificar?tab=historial' });

    expect(await screen.findByText(/Sofía Peralta/)).toBeInTheDocument();
    expect(screen.getByText(/Martín Acosta/)).toBeInTheDocument();
    expect(screen.getByText(/Camila Ibarra/)).toBeInTheDocument();
    expect(screen.getByText('En el Director')).toBeInTheDocument();
    expect(screen.getByText(/Motivo: “Documentación incompleta”/)).toBeInTheDocument();
  });

  it('offers a retry when the list fails to load', async () => {
    server.use(http.get(`${API}/graduation-requests`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })));
    renderWithProviders(<AdministrativoEgresosPorVerificar />, { route: '/administrativo/egresos-por-verificar' });

    expect(await screen.findByText('No pudimos cargar los egresos')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
  });
});
