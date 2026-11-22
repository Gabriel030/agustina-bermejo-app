import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Link, Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { TabAsistencia } from '@/features/attendance/pages/DocenteTomarAsistencia';
import { localIsoDate } from '@/lib/calendario';
import { rosterEntryFixture } from '@/test/fixtures/rosterEntries';

const API = 'http://localhost:8080';

const ROSTER = [
  rosterEntryFixture({ enrollmentId: 11, studentId: 1, studentName: 'Ana Gómez', dni: '30111222' }),
  rosterEntryFixture({ enrollmentId: 12, studentId: 2, studentName: 'Bruno Díaz', dni: '30111333' }),
  rosterEntryFixture({ enrollmentId: 13, studentId: 3, studentName: 'Carla Ruiz', dni: '30111444' }),
];

function mockEmptySection() {
  server.use(
    http.get(`${API}/program-sections/5/roster`, () => HttpResponse.json(ROSTER)),
    http.get(`${API}/program-sections/5/attendance/history`, () => HttpResponse.json({ rows: [] })),
    http.get(`${API}/program-sections/5/class-sessions/:date`, () => new HttpResponse(null, { status: 404 })),
  );
}

function rowOf(name) {
  return screen.getByText(name).closest('li');
}

describe('TabAsistencia take flow', () => {
  it('shows the unmarked pill, the progress and the remaining count until students are marked', async () => {
    mockEmptySection();
    const user = userEvent.setup();
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    expect(within(rowOf('Ana Gómez')).getByText('Sin marcar')).toBeInTheDocument();
    expect(screen.getByText('0 de 3 marcados')).toBeInTheDocument();
    expect(screen.getByText('Quedan 3 sin marcar.')).toBeInTheDocument();

    await user.click(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Tarde' }));

    expect(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Tarde' })).toHaveAttribute('aria-pressed', 'true');
    expect(within(rowOf('Ana Gómez')).queryByText('Sin marcar')).not.toBeInTheDocument();
    expect(screen.getByText('1 cambio sin guardar')).toBeInTheDocument();
    expect(screen.getByText('Quedan 2 sin marcar: se pueden guardar igual.')).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '33');
  });

  it('counts only pending students in the mark-all dialog', async () => {
    mockEmptySection();
    const user = userEvent.setup();
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    await user.click(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Ausente' }));
    await user.click(screen.getByRole('button', { name: 'Marcar presentes a los 2 sin marcar' }));

    expect(await screen.findByText('¿Marcar presentes a los 2 alumnos sin asistencia?')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Marcar presentes' }));

    expect(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Ausente' })).toHaveAttribute('aria-pressed', 'true');
    expect(within(rowOf('Bruno Díaz')).getByRole('button', { name: 'Presente' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('discards the changes and the stored draft after confirming', async () => {
    mockEmptySection();
    const user = userEvent.setup();
    renderWithProviders(<TabAsistencia type="PROGRAM" sectionId={5} />);

    await screen.findByText('Ana Gómez');
    await user.click(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Presente' }));
    await user.click(screen.getByRole('button', { name: 'Descartar' }));
    expect(await screen.findByText('¿Descartar 1 cambio?')).toBeInTheDocument();
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Descartar' }));

    expect(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Presente' })).toHaveAttribute('aria-pressed', 'false');
    expect(localStorage.getItem(`attendanceDraft:PROGRAM:5:${localIsoDate()}`)).toBeNull();
    expect(screen.getByRole('button', { name: 'Guardar asistencia' })).toBeDisabled();
  });

  it('asks before leaving through a link while there are unsaved changes', async () => {
    mockEmptySection();
    const user = userEvent.setup();
    renderWithProviders(
      <>
        <Link to="/mis-comisiones">Mis comisiones</Link>
        <TabAsistencia type="PROGRAM" sectionId={5} />
      </>,
    );

    await screen.findByText('Ana Gómez');
    await user.click(screen.getByRole('link', { name: 'Mis comisiones' }));
    expect(screen.queryByText('¿Salir sin guardar?')).not.toBeInTheDocument();

    await user.click(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Presente' }));
    await user.click(screen.getByRole('link', { name: 'Mis comisiones' }));

    expect(await screen.findByText('¿Salir sin guardar?')).toBeInTheDocument();
    expect(screen.getByText(/Tenés cambios sin guardar/)).toBeInTheDocument();
  });

  describe('exit guard', () => {
    async function renderDirty() {
      mockEmptySection();
      const user = userEvent.setup();
      renderWithProviders(
        <>
          <Link to="/mis-comisiones">Mis comisiones</Link>
          <Link to="/otra" target="_blank">Otra pestaña</Link>
          <TabAsistencia type="PROGRAM" sectionId={5} />
          <Routes>
            <Route path="/mis-comisiones" element={<div>Pantalla destino</div>} />
            <Route path="*" element={null} />
          </Routes>
        </>,
      );
      await screen.findByText('Ana Gómez');
      await user.click(within(rowOf('Ana Gómez')).getByRole('button', { name: 'Presente' }));
      return user;
    }

    it('registers beforeunload while there are changes and removes it after discarding', async () => {
      const added = vi.spyOn(window, 'addEventListener');
      const removed = vi.spyOn(window, 'removeEventListener');
      const user = await renderDirty();

      expect(added.mock.calls.some(([type]) => type === 'beforeunload')).toBe(true);
      await user.click(screen.getByRole('button', { name: 'Descartar' }));
      await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Descartar' }));

      await waitFor(() => expect(removed.mock.calls.some(([type]) => type === 'beforeunload')).toBe(true));
      added.mockRestore();
      removed.mockRestore();
    });

    it('navigates after confirming Salir', async () => {
      const user = await renderDirty();
      await user.click(screen.getByRole('link', { name: 'Mis comisiones' }));
      await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Salir' }));

      expect(await screen.findByText('Pantalla destino')).toBeInTheDocument();
    });

    it('stays when choosing Seguir acá', async () => {
      const user = await renderDirty();
      await user.click(screen.getByRole('link', { name: 'Mis comisiones' }));
      await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Seguir acá' }));

      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      expect(screen.queryByText('Pantalla destino')).not.toBeInTheDocument();
    });

    it('does not intercept ctrl-click or links that open in a new tab', async () => {
      const user = await renderDirty();
      await user.keyboard('{Control>}');
      await user.click(screen.getByRole('link', { name: 'Mis comisiones' }));
      await user.keyboard('{/Control}');
      await user.click(screen.getByRole('link', { name: 'Otra pestaña' }));

      expect(screen.queryByText('¿Salir sin guardar?')).not.toBeInTheDocument();
    });
  });
});
