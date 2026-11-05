import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';
import { renderWithProviders } from '@/test/renderWithProviders';
import { BandejaTramites } from './BandejaTramites';

const pending = [
  { id: 1, name: 'Reciente', at: '2026-10-20T10:00:00' },
  { id: 2, name: 'Antigua', at: '2026-09-01T10:00:00' },
];
const history = [{ id: 3, name: 'Decidida', at: '2026-08-01T10:00:00' }];

function LocationProbe() {
  const location = useLocation();
  return <div data-testid="search">{location.search}</div>;
}

function mount(route = '/tray') {
  return renderWithProviders(
    <>
      <Routes>
        <Route
          path="/tray"
          element={
            <BandejaTramites
              titulo="Bandeja de prueba"
              useListaPendientes={() => ({ data: pending, isLoading: false, isError: false })}
              useListaHistorial={() => ({ data: history, isLoading: false, isError: false })}
              renderFila={(item) => <div key={item.id}>{item.name}</div>}
              extraerTextoBusqueda={(item) => item.name}
              extraerFecha={(item) => new Date(item.at)}
            />
          }
        />
        <Route path="/director/aprobaciones" element={<div>Hub</div>} />
      </Routes>
      <LocationProbe />
    </>,
    { route },
  );
}

describe('BandejaTramites', () => {
  it('lists the oldest pending item first by default and shows the count per tab', () => {
    mount();

    const rows = screen.getAllByText(/Reciente|Antigua/).map((node) => node.textContent);
    expect(rows).toEqual(['Antigua', 'Reciente']);
    expect(screen.getByRole('tab', { name: 'Pendientes (2)' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Historial (1)' })).toBeInTheDocument();
  });

  it('keeps the tab and the search in the URL', async () => {
    const user = userEvent.setup();
    mount();

    await user.click(screen.getByRole('tab', { name: 'Historial (1)' }));
    expect(screen.getByTestId('search')).toHaveTextContent('tab=historial');
    expect(screen.getByText('Decidida')).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText(/Buscar/), 'zzz');
    expect(screen.getByTestId('search')).toHaveTextContent('q=zzz');
  });

  it('restores the state from the URL', () => {
    mount('/tray?tab=historial');

    expect(screen.getByText('Decidida')).toBeInTheDocument();
    expect(screen.queryByText('Antigua')).not.toBeInTheDocument();
  });

  it('renders the back button as a real link', () => {
    mount();

    expect(screen.getByRole('link', { name: /Aprobaciones/ })).toHaveAttribute('href', '/director/aprobaciones');
  });
});

describe('BandejaTramites — load failure', () => {
  function mountFailing(refetch, route = '/tray') {
    return renderWithProviders(
      <Routes>
        <Route
          path="/tray"
          element={
            <BandejaTramites
              titulo="Bandeja de prueba"
              useListaPendientes={() => ({ data: undefined, isLoading: false, isError: true, error: new Error('boom'), refetch })}
              useListaHistorial={() => ({ data: undefined, isLoading: false, isError: true, error: new Error('boom'), refetch })}
              renderFila={(item) => <div key={item.id}>{item.name}</div>}
              extraerTextoBusqueda={(item) => item.name}
              extraerFecha={(item) => new Date(item.at)}
            />
          }
        />
      </Routes>,
      { route },
    );
  }

  it('shows the error state instead of the empty state and retries the pending list', async () => {
    const refetch = vi.fn();
    const user = userEvent.setup();
    mountFailing(refetch);

    expect(screen.getByRole('alert')).toHaveTextContent('No pudimos cargar los trámites');
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(refetch).toHaveBeenCalledTimes(1);
  });

  it('hides the back link and relabels the tabs when configured', () => {
    renderWithProviders(
      <BandejaTramites
        titulo="Bandeja de prueba"
        volverA={null}
        etiquetaPendientes="Por verificar"
        contadorHistorial={false}
        useListaPendientes={() => ({ data: pending, isLoading: false, isError: false })}
        useListaHistorial={() => ({ data: history, isLoading: false, isError: false })}
        renderFila={(item) => <div key={item.id}>{item.name}</div>}
        extraerTextoBusqueda={(item) => item.name}
        extraerFecha={(item) => new Date(item.at)}
      />,
    );

    expect(screen.queryByRole('link', { name: /Aprobaciones/ })).not.toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Por verificar (2)' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Historial' })).toBeInTheDocument();
  });
});
