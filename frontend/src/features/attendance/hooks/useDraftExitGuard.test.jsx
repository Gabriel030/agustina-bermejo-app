import { describe, expect, it } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter, Link, Route, Routes, useLocation, useNavigate, useNavigationType } from 'react-router-dom';
import { renderWithProviders } from '@/test/renderWithProviders';
import { useDraftExitGuard } from '@/features/attendance/hooks/useDraftExitGuard';

function DraftScreen() {
  const { leaveDialog } = useDraftExitGuard(2, true);
  return (
    <>
      <Link to="/destino" replace state={{ origin: 'borrador' }}>Ir con opciones</Link>
      <Link to="/destino">Ir sin opciones</Link>
      {leaveDialog}
    </>
  );
}

function DestinationScreen() {
  const location = useLocation();
  const navigationType = useNavigationType();
  return <div data-testid="destination">{`${navigationType}:${location.state?.origin ?? 'sin estado'}`}</div>;
}

function renderDraftScreen() {
  return renderWithProviders(
    <Routes>
      <Route path="/borrador" element={<DraftScreen />} />
      <Route path="/destino" element={<DestinationScreen />} />
    </Routes>,
    { route: '/borrador' },
  );
}

describe('useDraftExitGuard', () => {
  it('keeps replace and state of the link the user confirmed leaving through', async () => {
    renderDraftScreen();
    const user = userEvent.setup();

    await user.click(screen.getByRole('link', { name: 'Ir con opciones' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Salir' }));

    expect(await screen.findByTestId('destination')).toHaveTextContent('REPLACE:borrador');
  });

  it('still navigates with a plain push when the link has no options', async () => {
    renderDraftScreen();
    const user = userEvent.setup();

    await user.click(screen.getByRole('link', { name: 'Ir sin opciones' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Salir' }));

    expect(await screen.findByTestId('destination')).toHaveTextContent('PUSH:sin estado');
  });
});

function BrowserDraftScreen() {
  const navigate = useNavigate();
  const { leaveDialog } = useDraftExitGuard(2, true);
  return (
    <>
      <Routes>
        <Route path="/borrador" element={<button onClick={() => navigate('/destino')}>Ir al destino</button>} />
        <Route path="/destino" element={<h2>Pantalla destino</h2>} />
      </Routes>
      {leaveDialog}
    </>
  );
}

describe('useDraftExitGuard with the browser back button', () => {
  it('asks before the back button leaves the draft and completes it when confirmed', async () => {
    window.history.replaceState(null, '', '/borrador');
    const user = userEvent.setup();
    render(
      <BrowserRouter>
        <BrowserDraftScreen />
      </BrowserRouter>,
    );
    await user.click(await screen.findByRole('button', { name: 'Ir al destino' }));
    expect(await screen.findByRole('heading', { name: 'Pantalla destino' })).toBeInTheDocument();

    window.history.back();
    const dialog = await screen.findByRole('dialog');
    await waitFor(() => expect(window.location.pathname).toBe('/destino'));
    await user.click(within(dialog).getByRole('button', { name: 'Salir' }));

    await waitFor(() => expect(window.location.pathname).toBe('/borrador'));
    expect(await screen.findByRole('button', { name: 'Ir al destino' })).toBeInTheDocument();
  });
});
