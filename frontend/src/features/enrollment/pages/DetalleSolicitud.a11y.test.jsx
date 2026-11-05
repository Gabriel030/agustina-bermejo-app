import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { axe } from '@/test/axe';
import { server } from '@/test/mocks/server';
import { errorBody } from '@/test/fixtures/errors';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DetalleSolicitud } from './DetalleSolicitud';

const API = 'http://localhost:8080';

function application(overrides) {
  return http.get(`${API}/me/program-enrollment-applications`, () =>
    HttpResponse.json([{ id: 1, programName: 'Diseño Gráfico', status: 'PENDING', requestedAt: '2026-03-02T10:00:00', ...overrides }]),
  );
}

function renderDetail() {
  return renderWithProviders(
    <Routes>
      <Route path="/mis-solicitudes/:id" element={<DetalleSolicitud />} />
    </Routes>,
    { route: '/mis-solicitudes/1' },
  );
}

describe('DetalleSolicitud accessibility', () => {
  it('pendingApplicationHasNoAxeViolations', async () => {
    server.use(application());
    const { container } = renderDetail();

    expect(await screen.findByRole('heading', { level: 1, name: 'Diseño Gráfico' })).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('eachDocumentActionHasItsOwnAccessibleName', async () => {
    server.use(application());
    renderDetail();

    expect(await screen.findByRole('button', { name: 'Subir archivo de DNI — frente' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Subir archivo de DNI — dorso' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Subir archivo de Analítico del secundario' })).toBeInTheDocument();
  });

  it('rejectedApplicationHasNoAxeViolations', async () => {
    server.use(application({ status: 'REJECTED', rejectionReason: 'Documentación ilegible' }));
    const { container } = renderDetail();

    expect(await screen.findByText(/Documentación ilegible/)).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('uploadErrorStateHasNoAxeViolations', async () => {
    server.use(
      application(),
      http.post(`${API}/me/program-enrollment-applications/1/documents`, () =>
        HttpResponse.json(errorBody('UNSUPPORTED_FILE_TYPE'), { status: 400 }),
      ),
    );
    const user = userEvent.setup();
    const { container } = renderDetail();
    const row = (await screen.findByText('DNI — frente')).closest('.border-b');

    await user.upload(row.querySelector('input[type="file"]'), new File(['x'], 'dni.pdf', { type: 'application/pdf' }));

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('replaceConfirmationDialogHasNoAxeViolations', async () => {
    server.use(
      application(),
      http.get(`${API}/me/documents`, () => HttpResponse.json([{ id: 1, type: 'DNI_FRENTE', uploadedAt: '2026-03-02T10:05:00' }])),
    );
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Reemplazar DNI — frente' }));

    const dialog = await screen.findByRole('dialog');
    expect(await axe(dialog)).toHaveNoViolations();
  });

  it('loadErrorStateHasNoAxeViolations', async () => {
    server.use(http.get(`${API}/me/program-enrollment-applications`, () => new HttpResponse(null, { status: 500 })));
    const { container } = renderDetail();

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });
});
