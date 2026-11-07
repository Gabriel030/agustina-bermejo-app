import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DirectorRevisarConfiguracion } from './DirectorRevisarConfiguracion';

globalThis.process.env.TZ ='America/Argentina/Buenos_Aires';

const API = 'http://localhost:8080';

function montar(sectionId) {
  return renderWithProviders(
    <Routes>
      <Route path="/director/revisar-configuracion/:sectionId" element={<DirectorRevisarConfiguracion />} />
    </Routes>,
    { route: `/director/revisar-configuracion/${sectionId}` },
  );
}

describe('DirectorRevisarConfiguracion', () => {
  it('muestra las fechas de la propuesta en el mismo día que cargó el docente', async () => {
    server.use(
      http.get(`${API}/section-evaluation-configs/42`, () => HttpResponse.json(fullConfig())),
      http.get(`${API}/holidays`, () => HttpResponse.json([])),
    );

    montar(42);

    expect(await screen.findByDisplayValue(/^24 de abr/)).toBeInTheDocument();
    expect(screen.getByDisplayValue(/^15 de may/)).toBeInTheDocument();
  });

  it('una configuración inexistente muestra un estado de error en vez de cargar para siempre', async () => {
    server.use(
      http.get(`${API}/section-evaluation-configs/99999`, () => new HttpResponse(null, { status: 404 })),
      http.get(`${API}/holidays`, () => HttpResponse.json([])),
    );

    montar(99999);

    expect(await screen.findByText('Este trámite no existe o ya no está disponible')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Volver a la bandeja/ })).toBeInTheDocument();
  });

  it('does not show a not found message when loading fails with a server error', async () => {
    server.use(
      http.get(`${API}/section-evaluation-configs/7`, () => new HttpResponse(null, { status: 500 })),
      http.get(`${API}/holidays`, () => HttpResponse.json([])),
    );

    montar(7);

    expect(await screen.findByText('No pudimos cargar el trámite')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByText('Este trámite no existe o ya no está disponible')).not.toBeInTheDocument();
  });

  function fullConfig(status = 'PENDING_APPROVAL') {
    return {
          id: 1,
          canEdit: false,
          canSubmit: false,
          evaluationSchemeName: 'Esquema',
          exam3Date: '2026-06-01',
          exam4Date: '2026-06-02',
          tp2DueDate: '2026-05-01',
          tp3DueDate: '2026-05-02',
          minApprovalGrade: 4,
          minRegularGrade: 4,
          promotionMinGrade: 7,
          rejectionReason: 'x',
          reviewedAt: '2026-10-02T10:00:00',
          reviewedByName: 'Marcela',
          submittedAt: '2026-10-01T10:00:00',
          termEndDate: '2026-12-11T00:00:00',
          termEnrollmentStart: '2026-02-01T00:00:00',
          termStartDate: '2026-03-02T00:00:00',
          tp1DueDate: '2026-04-24',
          exam1Date: '2026-05-15',
          exam2Date: '2026-09-11',
          finalCall1Date: '2026-12-02',
          finalCall2Date: '2026-12-16',
          subjectSectionId: 42,
          subjectName: 'Sujeto y Educación',
          sectionName: 'Comisión A',
          status,
          tpCount: 1,
          examCountExpected: 2,
          hasFinal: true,
          instructors: [],
    };
  }

  function mockPending(status = 'PENDING_APPROVAL') {
    server.use(
      http.get(`${API}/section-evaluation-configs/42`, () => HttpResponse.json(fullConfig(status))),
      http.get(`${API}/holidays`, () => HttpResponse.json([])),
    );
  }

  function mountWithTray() {
    return renderWithProviders(
      <Routes>
        <Route path="/director/revisar-configuracion/:sectionId" element={<DirectorRevisarConfiguracion />} />
        <Route path="/director/aprobaciones/configuracion-evaluacion" element={<div>Bandeja de configuración</div>} />
      </Routes>,
      { route: '/director/revisar-configuracion/42' },
    );
  }

  it('approves after confirming the summary dialog', async () => {
    mockPending();
    server.use(http.post(`${API}/section-evaluation-configs/42/approve`, () => HttpResponse.json(fullConfig('APPROVED'))));
    const user = userEvent.setup();
    mountWithTray();

    await user.click(await screen.findByRole('button', { name: 'Aprobar' }));
    expect(await screen.findByText('Aprobar configuración')).toBeInTheDocument();
    await user.click(screen.getAllByRole('button', { name: 'Aprobar' }).at(-1));

    expect(await screen.findByText('Bandeja de configuración')).toBeInTheDocument();
  });

  it('rejects with a reason of at least 10 characters', async () => {
    mockPending();
    let sent;
    server.use(
      http.post(`${API}/section-evaluation-configs/42/reject`, async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json(fullConfig('REJECTED'));
      }),
    );
    const user = userEvent.setup();
    mountWithTray();

    await user.click(await screen.findByRole('button', { name: 'Rechazar' }));
    expect(await screen.findByText('Rechazar configuración')).toBeInTheDocument();
    const field = screen.getByPlaceholderText(/faltan fechas/);
    await user.type(field, 'corto');
    await user.click(screen.getAllByRole('button', { name: 'Rechazar' }).at(-1));
    expect(await screen.findByRole('alert')).toHaveTextContent('al menos 10 caracteres');
    await user.clear(field);
    await user.type(field, 'Faltan las fechas de los TP');
    await user.click(screen.getAllByRole('button', { name: 'Rechazar' }).at(-1));

    expect(await screen.findByText('Bandeja de configuración')).toBeInTheDocument();
    expect(sent).toEqual({ reason: 'Faltan las fechas de los TP' });
  });

  it('hides the decision buttons for an already decided configuration', async () => {
    mockPending('APPROVED');
    mountWithTray();

    await screen.findByText(/Sujeto y Educación/);
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Aprobar' })).not.toBeInTheDocument());
    expect(screen.queryByRole('button', { name: 'Rechazar' })).not.toBeInTheDocument();
  });
});
