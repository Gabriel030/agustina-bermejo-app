import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes, useLocation } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installFixedClock } from '@/test/fixedClock';
import { createDeferred } from '@/test/deferred';
import { REQUESTS_FIXTURE, attendanceChangeRequest } from '@/test/fixtures/attendanceRequests';
import { rosterEntryFixture } from '@/test/fixtures/rosterEntries';
import { DocenteTomarAsistencia } from '@/features/attendance/pages/DocenteTomarAsistencia';

const API = 'http://localhost:8080';

const SECTIONS = [
  { type: 'PROGRAM', id: 5, subjectName: 'Programación II', sectionName: 'Comisión A', termStartDate: '2026-03-01T00:00:00Z', termEndDate: '2026-12-15T00:00:00Z' },
  { type: 'PROGRAM', id: 6, subjectName: 'Bases de Datos', sectionName: 'Comisión B', termStartDate: '2026-03-01T00:00:00Z', termEndDate: '2026-12-15T00:00:00Z' },
];

const HISTORY = {
  classDates: ['2026-10-30', '2026-11-04'],
  rows: [
    {
      studentId: 1,
      studentName: 'Valentina Suárez',
      attendancePercent: 80,
      cellsByDate: {
        '2026-11-04': { attendanceId: 901, status: 'ABSENT', justificationDeadline: null, justifiable: false, rejection: null },
      },
    },
  ],
};

const ROSTER = [rosterEntryFixture({ enrollmentId: 11, studentId: 1, studentName: 'Ana Gómez', dni: '30111222' })];

function mockApi({ sections = SECTIONS, requests = REQUESTS_FIXTURE, history = HISTORY } = {}) {
  server.use(
    http.get(`${API}/me/teaching-sections`, () => HttpResponse.json(sections)),
    http.get(`${API}/me/attendance-change-requests`, () => HttpResponse.json(requests)),
    http.get(`${API}/program-sections/5/attendance/history`, () => HttpResponse.json(history)),
    http.get(`${API}/program-sections/5/attendance/absence-report`, () => HttpResponse.json([])),
    http.get(`${API}/program-sections/5/roster`, () => HttpResponse.json(ROSTER)),
    http.get(`${API}/program-sections/5/class-sessions/:date`, () => new HttpResponse(null, { status: 404 })),
  );
}

function mockExport(handler) {
  server.use(http.get(`${API}/program-sections/5/attendance/export`, handler));
}

function exportFile() {
  return new HttpResponse('contenido', { headers: { 'Content-Disposition': 'attachment; filename="asistencia.pdf"' } });
}

function LocationProbe() {
  const location = useLocation();
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>;
}

function renderPage(route) {
  return renderWithProviders(
    <>
      <Routes>
        <Route path="/docente/tomar-asistencia" element={<DocenteTomarAsistencia />} />
      </Routes>
      <LocationProbe />
    </>,
    { route },
  );
}

describe('DocenteTomarAsistencia single tab level', () => {
  installFixedClock();

  it('shows one h1 and one tablist with three tabs named by view', async () => {
    mockApi();
    renderPage('/docente/tomar-asistencia?seccion=PROGRAM-5');

    expect(await screen.findByRole('tab', { name: 'Pasar lista' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getAllByRole('tablist')).toHaveLength(1);
    expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['Pasar lista', 'Historial', 'Reporte']);
    expect(screen.getByRole('tab', { name: 'Reporte de asistencia' })).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it('switches view clearing the fecha and pedido params but keeping the commission', async () => {
    const user = userEvent.setup();
    mockApi();
    renderPage('/docente/tomar-asistencia?seccion=PROGRAM-5&fecha=2026-11-04&pedido=40');

    await user.click(await screen.findByRole('tab', { name: 'Historial' }));

    expect(screen.getByTestId('location')).toHaveTextContent('/docente/tomar-asistencia?seccion=PROGRAM-5&tab=historial');
  });

  it.each(['asistencia', 'exportar'])('rewrites the legacy tab=%s alias to pasar-lista keeping fecha and pedido', async (alias) => {
    mockApi();
    renderPage(`/docente/tomar-asistencia?tab=${alias}&seccion=PROGRAM-5&fecha=2026-11-04&pedido=40`);

    expect(await screen.findByRole('tab', { name: 'Pasar lista' })).toHaveAttribute('aria-selected', 'true');
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(`tab=pasar-lista`));
    expect(screen.getByTestId('location')).toHaveTextContent('seccion=PROGRAM-5');
    expect(screen.getByTestId('location')).toHaveTextContent('fecha=2026-11-04');
    expect(screen.getByTestId('location')).toHaveTextContent('pedido=40');
  });

  it('offers Exportar with the active view first and opens the dialog with that scope', async () => {
    const user = userEvent.setup();
    mockApi();
    renderPage('/docente/tomar-asistencia?tab=historial&seccion=PROGRAM-5');

    await screen.findByText('Valentina Suárez');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Exportar' })).not.toHaveAttribute('aria-disabled'));
    await user.click(screen.getByRole('button', { name: 'Exportar' }));

    const items = await screen.findAllByRole('menuitem');
    expect(items.map((item) => item.textContent)).toEqual(['HistorialEsta vista', 'Asistencia de una clase', 'Reporte']);
    await user.click(items[0]);

    const dialog = await screen.findByRole('dialog', { name: 'Exportar historial' });
    expect(within(dialog).queryByRole('combobox', { name: 'Clase' })).not.toBeInTheDocument();
    expect(within(dialog).getByRole('radiogroup', { name: 'Formato' })).toBeInTheDocument();
  });

  it('asks for year and class when exporting a single class and downloads the file returning focus to Exportar', async () => {
    const user = userEvent.setup();
    URL.createObjectURL = vi.fn(() => 'blob:attendance');
    URL.revokeObjectURL = vi.fn();
    let query;
    mockApi();
    mockExport(({ request }) => {
      query = new URL(request.url).searchParams;
      return exportFile();
    });
    renderPage('/docente/tomar-asistencia?seccion=PROGRAM-5');

    await waitFor(() => expect(screen.getByRole('button', { name: 'Exportar' })).not.toHaveAttribute('aria-disabled'));
    await user.click(screen.getByRole('button', { name: 'Exportar' }));
    await user.click(await screen.findByRole('menuitem', { name: /Asistencia de una clase/ }));

    const dialog = await screen.findByRole('dialog', { name: 'Exportar asistencia de una clase' });
    expect(within(dialog).getByRole('combobox', { name: 'Clase' })).toHaveTextContent('2026-11-04');
    await user.click(within(dialog).getByRole('radio', { name: 'XLS' }));
    await user.click(within(dialog).getByRole('button', { name: 'Exportar' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(Object.fromEntries(query)).toEqual({ format: 'xlsx', scope: 'class', classDate: '2026-11-04' });
    await waitFor(() => expect(screen.getByRole('button', { name: 'Exportar' })).toHaveFocus());
  });

  it('shows a pending state that cannot be dismissed while the file is generated', async () => {
    const user = userEvent.setup();
    URL.createObjectURL = vi.fn(() => 'blob:attendance');
    URL.revokeObjectURL = vi.fn();
    const gate = createDeferred();
    mockApi();
    mockExport(async () => {
      await gate.promise;
      return exportFile();
    });
    renderPage('/docente/tomar-asistencia?tab=reporte&seccion=PROGRAM-5');

    await waitFor(() => expect(screen.getByRole('button', { name: 'Exportar' })).not.toHaveAttribute('aria-disabled'));
    await user.click(screen.getByRole('button', { name: 'Exportar' }));
    await user.click(await screen.findByRole('menuitem', { name: /Reporte/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Exportar reporte' });
    await user.click(within(dialog).getByRole('button', { name: 'Exportar' }));

    expect(await within(dialog).findByRole('button', { name: /Generando/ })).toBeDisabled();
    expect(within(dialog).getByRole('button', { name: 'Cancelar' })).toBeDisabled();
    expect(within(dialog).queryByRole('button', { name: 'Cerrar' })).not.toBeInTheDocument();
    await user.keyboard('{Escape}');
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    gate.resolve();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('shows an actionable error inside the dialog and recovers on retry', async () => {
    const user = userEvent.setup();
    URL.createObjectURL = vi.fn(() => 'blob:attendance');
    URL.revokeObjectURL = vi.fn();
    let calls = 0;
    mockApi();
    mockExport(() => {
      calls += 1;
      return calls === 1 ? HttpResponse.json({ code: 'INTERNAL_ERROR', retryable: false }, { status: 500 }) : exportFile();
    });
    renderPage('/docente/tomar-asistencia?tab=historial&seccion=PROGRAM-5');

    await waitFor(() => expect(screen.getByRole('button', { name: 'Exportar' })).not.toHaveAttribute('aria-disabled'));
    await user.click(screen.getByRole('button', { name: 'Exportar' }));
    await user.click(await screen.findByRole('menuitem', { name: /Historial/ }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Exportar' }));

    const alert = await within(dialog).findByRole('alert');
    expect(alert).toHaveTextContent('Tuvimos un problema de nuestro lado');
    await user.click(within(alert).getByRole('button', { name: 'Reintentar' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(calls).toBe(2);
  });

  it('keeps Exportar disabled with a visible reason and does not open the menu', async () => {
    const user = userEvent.setup();
    mockApi({ history: { classDates: [], rows: [] } });
    renderPage('/docente/tomar-asistencia');

    const exportButton = await screen.findByRole('button', { name: 'Exportar' });
    expect(exportButton).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getByText('Elegí una comisión para exportar')).toBeInTheDocument();
    await user.click(exportButton);
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    exportButton.focus();
    await user.keyboard('{Enter}');
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('explains why Exportar is disabled without classes and inside Mis pedidos', async () => {
    mockApi({ history: { classDates: [], rows: [] } });
    const first = renderPage('/docente/tomar-asistencia?seccion=PROGRAM-5');

    expect(await screen.findByText('Todavía no diste ninguna clase')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Exportar' })).toHaveAttribute('aria-disabled', 'true');
    first.unmount();

    renderPage('/docente/tomar-asistencia?tab=pedidos&seccion=PROGRAM-5');
    expect(await screen.findByText('Elegí Pasar lista, Historial o Reporte para exportar')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Exportar' })).toHaveAttribute('aria-disabled', 'true');
  });

  it('explains that classes are loading while the history is pending', async () => {
    const gate = createDeferred();
    mockApi();
    server.use(
      http.get(`${API}/program-sections/5/attendance/history`, async () => {
        await gate.promise;
        return HttpResponse.json(HISTORY);
      }),
    );
    renderPage('/docente/tomar-asistencia?seccion=PROGRAM-5');

    expect(await screen.findByText('Cargando clases…')).toBeInTheDocument();
    gate.resolve();
    await waitFor(() => expect(screen.queryByText('Cargando clases…')).not.toBeInTheDocument());
  });
});

describe('DocenteTomarAsistencia Mis pedidos subview', () => {
  installFixedClock();

  it('moves focus to the subview heading and back to the header button when leaving', async () => {
    const user = userEvent.setup();
    mockApi();
    renderPage('/docente/tomar-asistencia?seccion=PROGRAM-5');

    await user.click(await screen.findByRole('link', { name: /Mis pedidos/ }));
    expect(await screen.findByRole('heading', { level: 2, name: 'Mis pedidos' })).toHaveFocus();

    await user.click(screen.getByRole('link', { name: 'Volver a Pasar lista' }));
    await waitFor(() => expect(screen.getByRole('link', { name: /Mis pedidos/ })).toHaveFocus());
    expect(screen.getByRole('tab', { name: 'Pasar lista' })).toBeInTheDocument();
  });

  it('keeps the commission in the header link, the back link and the history links', async () => {
    mockApi();
    renderPage('/docente/tomar-asistencia?seccion=PROGRAM-5');

    expect(await screen.findByRole('link', { name: /Mis pedidos/ })).toHaveAttribute('href', '/docente/tomar-asistencia?tab=pedidos&seccion=PROGRAM-5');
  });

  it('goes back to the commission of the open request when the url has no seccion', async () => {
    mockApi();
    renderPage('/docente/tomar-asistencia?tab=pedidos&pedido=40');

    expect(await screen.findByRole('link', { name: 'Volver a Pasar lista' })).toHaveAttribute('href', '/docente/tomar-asistencia?seccion=PROGRAM-6');
  });

  it('keeps the commission in the return link of the history banner', async () => {
    mockApi({ requests: [attendanceChangeRequest({ id: 40, attendanceId: 901, status: 'REJECTED', rejectionReason: 'No.' })] });
    renderPage('/docente/tomar-asistencia?tab=historial&seccion=PROGRAM-5&pedido=40');

    expect(await screen.findByRole('link', { name: 'Volver a Mis pedidos' })).toHaveAttribute('href', '/docente/tomar-asistencia?tab=pedidos&seccion=PROGRAM-5');
  });

  it('asks before leaving to Mis pedidos with an unsaved draft', async () => {
    const user = userEvent.setup();
    mockApi();
    renderPage('/docente/tomar-asistencia?seccion=PROGRAM-5');

    await screen.findByText('Ana Gómez');
    await user.click(screen.getByRole('button', { name: 'Presente' }));
    await user.click(screen.getByRole('link', { name: /Mis pedidos/ }));

    expect(await screen.findByText('¿Salir sin guardar?')).toBeInTheDocument();
    expect(screen.getByTestId('location')).not.toHaveTextContent('tab=pedidos');
  });
});

describe('DocenteTomarAsistencia page states', () => {
  installFixedClock();

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows the empty state when the teacher has no commissions', async () => {
    mockApi({ sections: [] });
    renderPage('/docente/tomar-asistencia');

    expect(await screen.findByText('Todavía no tenés comisiones')).toBeInTheDocument();
  });

  it('shows a retryable error instead of the empty state when the commissions fail to load', async () => {
    const user = userEvent.setup();
    mockApi();
    let calls = 0;
    server.use(
      http.get(`${API}/me/teaching-sections`, () => {
        calls += 1;
        return calls <= 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(SECTIONS);
      }),
    );
    renderPage('/docente/tomar-asistencia');

    expect(await screen.findByText(/No pudimos cargar tus comisiones/)).toBeInTheDocument();
    expect(screen.queryByText('Todavía no tenés comisiones')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('combobox')).toHaveTextContent('Elegí una comisión');
  });
});
