import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DataImportDetailPage } from './pages/DataImportDetailPage';
import { DataImportRollbackPage } from './pages/DataImportRollbackPage';

const API = 'http://localhost:8080';
const STAMP = '2026-11-12T10:00:00';
const PERSONS_FILE = { entityType: 'PERSONS', originalName: 'personas.csv', rowCount: 120, sizeBytes: 20480, uploadedAt: STAMP };

function batch(overrides = {}) {
  return {
    id: 5,
    name: 'Carga inicial 2026',
    status: 'COMMITTED',
    createdAt: STAMP,
    validatedAt: STAMP,
    committedAt: STAMP,
    sealedAt: STAMP,
    files: [PERSONS_FILE],
    counters: [{ entityType: 'PERSONS', rowsByStatus: { COMMITTED: 120 } }],
    ...overrides,
  };
}

function serveBatch(value) {
  server.use(http.get(`${API}/data-imports/5`, () => HttpResponse.json(value)));
}

function renderDetail() {
  return renderWithProviders(
    <Routes>
      <Route path="/director/importacion-inicial/:id" element={<DataImportDetailPage />} />
    </Routes>,
    { route: '/director/importacion-inicial/5' },
  );
}

function renderRollback() {
  return renderWithProviders(
    <Routes>
      <Route path="/director/importacion-inicial/:id/deshacer" element={<DataImportRollbackPage />} />
    </Routes>,
    { route: '/director/importacion-inicial/5/deshacer' },
  );
}

function mockDownloads() {
  URL.createObjectURL = vi.fn(() => 'blob:mock');
  URL.revokeObjectURL = vi.fn();
}

const PREVIEW = {
  canRollback: true,
  blockers: [],
  created: [{ entityType: 'PERSONS', total: 120 }],
  retained: [{ entityType: 'PROGRAMS', total: 12 }],
};

describe('DataImport seal and retry', () => {
  it('requires the report download and the typed word before sealing', async () => {
    mockDownloads();
    let sealBody = null;
    serveBatch(batch());
    server.use(
      http.get(`${API}/data-imports/5/report.csv`, () => new HttpResponse('a,b', { headers: { 'Content-Type': 'text/csv' } })),
      http.post(`${API}/data-imports/5/seal`, async ({ request }) => {
        sealBody = await request.json();
        serveBatch(batch({ status: 'SEALED' }));
        return HttpResponse.json(batch({ status: 'SEALED' }));
      }),
    );
    renderDetail();
    const sealButton = await screen.findByRole('button', { name: 'Cerrar la importación' });
    expect(sealButton).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Descargar reporte' }));
    await waitFor(() => expect(sealButton).toBeEnabled());
    await userEvent.click(sealButton);
    const dialog = await screen.findByRole('alertdialog');
    const confirm = within(dialog).getByRole('button', { name: 'Cerrar la importación' });
    expect(confirm).toBeDisabled();
    await userEvent.type(within(dialog).getByLabelText(/escribí CERRAR/), ' cerrar ');
    await userEvent.click(confirm);
    await waitFor(() => expect(sealBody).toEqual({ confirmation: ' cerrar ' }));
    expect(await screen.findByText(/Cerrada el/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cerrar la importación' })).not.toBeInTheDocument();
    expect(screen.queryByText('Deshacer esta importación…')).not.toBeInTheDocument();
  });

  it('retries a partial import', async () => {
    let retried = false;
    serveBatch(batch({ status: 'PARTIAL' }));
    server.use(
      http.post(`${API}/data-imports/5/retry`, () => {
        retried = true;
        return HttpResponse.json(batch({ status: 'COMMITTING' }));
      }),
    );
    renderDetail();
    await userEvent.click(await screen.findByRole('button', { name: 'Reintentar lo que falta' }));
    await waitFor(() => expect(retried).toBe(true));
  });

  it('downloads the template from the API', async () => {
    mockDownloads();
    let requested = false;
    serveBatch(batch({ status: 'DRAFT', counters: [] }));
    server.use(
      http.get(`${API}/data-imports/templates/PERSONS`, () => {
        requested = true;
        return new HttpResponse('a,b', { headers: { 'Content-Type': 'text/csv' } });
      }),
    );
    renderDetail();
    await userEvent.click(await screen.findByRole('button', { name: 'Plantilla' }));
    await waitFor(() => expect(requested).toBe(true));
  });
});

describe('DataImport rollback', () => {
  it('previews what is deleted and what is kept, and rolls back with the typed word', async () => {
    let rollbackBody = null;
    serveBatch(batch());
    server.use(
      http.get(`${API}/data-imports/5/rollback-preview`, () => HttpResponse.json(PREVIEW)),
      http.post(`${API}/data-imports/5/rollback`, async ({ request }) => {
        rollbackBody = await request.json();
        return HttpResponse.json(batch({ status: 'ROLLED_BACK' }));
      }),
    );
    renderRollback();
    const deleted = await screen.findByRole('list', { name: 'Se va a borrar' });
    expect(deleted).toHaveTextContent('Personas');
    expect(deleted).toHaveTextContent('120');
    expect(screen.getByRole('list', { name: 'Se conserva' })).toHaveTextContent('Carreras');
    await userEvent.click(screen.getByRole('button', { name: 'Deshacer la importación' }));
    const dialog = await screen.findByRole('alertdialog');
    const confirm = within(dialog).getByRole('button', { name: 'Deshacer la importación' });
    expect(confirm).toBeDisabled();
    await userEvent.type(within(dialog).getByLabelText(/escribí DESHACER/), 'deshacer');
    await userEvent.click(confirm);
    await waitFor(() => expect(rollbackBody).toEqual({ confirmation: 'deshacer' }));
  });

  it('lists the blockers and disables the rollback when the preview says it cannot', async () => {
    serveBatch(batch());
    server.use(
      http.get(`${API}/data-imports/5/rollback-preview`, () =>
        HttpResponse.json({
          ...PREVIEW,
          canRollback: false,
          blockers: [{ entityType: 'PERSONS', naturalKey: 'DNI 123', reason: 'La persona ya inició sesión' }],
        }),
      ),
    );
    renderRollback();
    expect(await screen.findByText(/La persona ya inició sesión/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Deshacer la importación' })).toBeDisabled();
  });

  it('shows the blockers returned by a 409 when the rollback is refused', async () => {
    serveBatch(batch());
    server.use(
      http.get(`${API}/data-imports/5/rollback-preview`, () => HttpResponse.json(PREVIEW)),
      http.post(`${API}/data-imports/5/rollback`, () =>
        HttpResponse.json(
          {
            code: 'IMPORT_ROLLBACK_BLOCKED',
            retryable: false,
            details: { blockers: [{ entityType: 'PERSONS', naturalKey: 'DNI 9', reason: 'Una persona cursó' }] },
          },
          { status: 409 },
        ),
      ),
    );
    renderRollback();
    await userEvent.click(await screen.findByRole('button', { name: 'Deshacer la importación' }));
    const dialog = await screen.findByRole('alertdialog');
    await userEvent.type(within(dialog).getByLabelText(/escribí DESHACER/), 'DESHACER');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Deshacer la importación' }));
    expect(await screen.findByRole('list', { name: 'Motivos' })).toHaveTextContent('Una persona cursó');
  });
});
