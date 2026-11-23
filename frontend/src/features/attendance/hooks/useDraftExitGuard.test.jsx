import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, Route, Routes, useLocation, useNavigationType } from 'react-router-dom';
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
