import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DataImportDetailPage } from './pages/DataImportDetailPage';
import { DataImportListPage } from './pages/DataImportListPage';
import { blockProgress, phaseOf } from './importLabels';

const API = 'http://localhost:8080';
const STAMP = '2026-11-12T10:00:00';

function batch(overrides = {}) {
  return {
    id: 5,
    name: 'Carga inicial 2026',
    status: 'DRAFT',
    createdAt: STAMP,
    validatedAt: STAMP,
    committedAt: STAMP,
    sealedAt: STAMP,
    graduationsPendingReview: 0,
    files: [],
    counters: [],
    ...overrides,
  };
}

const PERSONS_FILE = { entityType: 'PERSONS', originalName: 'personas.csv', rowCount: 120, sizeBytes: 20480, uploadedAt: STAMP };

function renderDetail() {
  return renderWithProviders(
    <Routes>
      <Route path="/director/importacion-inicial/:id" element={<DataImportDetailPage />} />
    </Routes>,
    { route: '/director/importacion-inicial/5' },
  );
}

function serveBatch(value) {
  server.use(http.get(`${API}/data-imports/5`, () => HttpResponse.json(value)));
}

describe('DataImport helpers', () => {
  it('maps each status to the step that has to be shown', () => {
    expect(phaseOf('DRAFT')).toBe('files');
    expect(phaseOf('VALIDATION_FAILED')).toBe('review');
    expect(phaseOf('COMMITTING')).toBe('progress');
    expect(phaseOf('PARTIAL')).toBe('report');
  });

  it('computes block progress only over the rows that will be imported', () => {
    expect(blockProgress({ rowsByStatus: { COMMITTED: 50, VALID: 50, ERROR: 10, UNCHANGED: 5 } })).toBe(50);
    expect(blockProgress({ rowsByStatus: { ERROR: 3 } })).toBe(0);
  });
});

describe('DataImportListPage', () => {
  it('lists imports with a readable status and content summary', async () => {
    server.use(
      http.get(`${API}/data-imports`, () =>
        HttpResponse.json([batch({ status: 'VALIDATION_FAILED', files: [PERSONS_FILE] })]),
      ),
    );
    renderWithProviders(<DataImportListPage />, { route: '/director/importacion-inicial' });
    expect(await screen.findByText('Carga inicial 2026')).toBeInTheDocument();
    expect(screen.getAllByText('Con errores').length).toBeGreaterThan(0);
    expect(screen.getByText('1 bloque · 120 filas')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Nueva importación/ })).toHaveAttribute('href', '/director/importacion-inicial/nueva');
  });

  it('shows an empty state when there are no imports', async () => {
    server.use(http.get(`${API}/data-imports`, () => HttpResponse.json([])));
    renderWithProviders(<DataImportListPage />, { route: '/director/importacion-inicial' });
    expect(await screen.findByText('Todavía no hay importaciones')).toBeInTheDocument();
  });
});

describe('DataImportDetailPage', () => {
  it('offers every backend block in dependency order, with review disabled until a file is loaded', async () => {
    serveBatch(batch());
    renderDetail();
    expect(await screen.findByTestId('block-PERSONS')).toBeInTheDocument();
    expect(screen.queryByText('Próximamente')).not.toBeInTheDocument();
    const order = ['PERSONS', 'PROGRAMS', 'SUBJECTS', 'CURRICULUM', 'PREREQUISITES', 'TERMS', 'SECTIONS', 'PROGRAM_ENROLLMENTS', 'SECTION_ENROLLMENTS', 'GRADE_ITEMS', 'ATTENDANCES', 'GRADUATIONS'];
    const rendered = screen.getAllByTestId(/^block-/).map((item) => item.getAttribute('data-testid').replace('block-', ''));
    expect(rendered).toEqual(order);
    expect(screen.getByText('Períodos lectivos')).toBeInTheDocument();
    expect(screen.getByText('Notas por examen y parcial')).toBeInTheDocument();
    expect(screen.getByText('Asistencia por clase')).toBeInTheDocument();
    expect(screen.getByText('Egresos')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Revisar archivos' })).toBeDisabled();
  });

  it('uploads a csv file to a non-persons block with the same flow', async () => {
    let uploaded = null;
    serveBatch(batch());
    server.use(
      http.post(`${API}/data-imports/5/files/TERMS`, async () => {
        uploaded = 'TERMS';
        const after = batch({ status: 'FILES_UPLOADED', files: [{ ...PERSONS_FILE, entityType: 'TERMS', originalName: 'periodos.csv' }] });
        serveBatch(after);
        return HttpResponse.json(after);
      }),
    );
    renderDetail();
    const input = await screen.findByLabelText('Archivo de Períodos lectivos');
    await userEvent.upload(input, new File(['year\n2026'], 'periodos.csv', { type: 'text/csv' }));
    await waitFor(() => expect(uploaded).toBe('TERMS'));
    expect(await screen.findByText('periodos.csv')).toBeInTheDocument();
  });

  it('uploads a csv file to the persons block', async () => {
    let uploaded = null;
    serveBatch(batch());
    server.use(
      http.post(`${API}/data-imports/5/files/PERSONS`, async () => {
        uploaded = 'PERSONS';
        const after = batch({ status: 'FILES_UPLOADED', files: [PERSONS_FILE] });
        serveBatch(after);
        return HttpResponse.json(after);
      }),
    );
    renderDetail();
    const input = await screen.findByLabelText('Archivo de Personas');
    await userEvent.upload(input, new File(['dni\n1'], 'personas.csv', { type: 'text/csv' }));
    await waitFor(() => expect(uploaded).toBe('PERSONS'));
    expect(await screen.findByText('personas.csv')).toBeInTheDocument();
  });

  it('blocks the import when the review found errors and lists them', async () => {
    serveBatch(
      batch({
        status: 'VALIDATION_FAILED',
        files: [PERSONS_FILE],
        counters: [{ entityType: 'PERSONS', rowsByStatus: { VALID: 118, ERROR: 2 } }],
      }),
    );
    server.use(
      http.get(`${API}/data-imports/5/errors`, () =>
        HttpResponse.json({
          items: [
            { code: 'EMAIL_ALREADY_REGISTERED', column: 'email', entityType: 'PERSONS', message: 'El mail ya está registrado', rowNumber: 7, rowStatus: 'ERROR' },
          ],
          page: 0,
          size: 50,
          totalElements: 1,
        }),
      ),
    );
    renderDetail();
    expect(await screen.findByText('El mail ya está registrado')).toBeInTheDocument();
    expect(screen.getByTestId('count-ERROR')).toHaveTextContent('2');
    expect(screen.getByRole('button', { name: 'Importar' })).toBeDisabled();
  });

  it('commits only after the confirmation and shows the report', async () => {
    let committedBody = null;
    const validated = batch({
      status: 'VALIDATED',
      files: [PERSONS_FILE],
      counters: [{ entityType: 'PERSONS', rowsByStatus: { VALID: 120 } }],
    });
    serveBatch(validated);
    server.use(
      http.post(`${API}/data-imports/5/commit`, async ({ request }) => {
        committedBody = await request.json();
        serveBatch(batch({ status: 'COMMITTED', files: [PERSONS_FILE], counters: [{ entityType: 'PERSONS', rowsByStatus: { COMMITTED: 120 } }] }));
        return HttpResponse.json(batch({ status: 'COMMITTED', files: [PERSONS_FILE], counters: [{ entityType: 'PERSONS', rowsByStatus: { COMMITTED: 120 } }] }));
      }),
    );
    renderDetail();
    await userEvent.click(await screen.findByRole('button', { name: 'Importar' }));
    expect(committedBody).toBeNull();
    const dialog = await screen.findByRole('alertdialog');
    expect(dialog).toHaveTextContent('Se van a crear 120 personas');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Importar' }));
    await waitFor(() => expect(committedBody).toEqual({ onlyValidRows: false }));
    expect(await screen.findByTestId('report-COMMITTED')).toHaveTextContent('120');
  });

  it('shows the progress panel while the batch is committing', async () => {
    serveBatch(
      batch({
        status: 'COMMITTING',
        files: [PERSONS_FILE],
        counters: [{ entityType: 'PERSONS', rowsByStatus: { COMMITTED: 60, VALID: 60 } }],
      }),
    );
    renderDetail();
    expect(await screen.findByTestId('import-progress-done')).toHaveTextContent('60');
    expect(screen.getByRole('progressbar', { name: 'Personas' })).toHaveAttribute('aria-valuenow', '50');
  });
});
