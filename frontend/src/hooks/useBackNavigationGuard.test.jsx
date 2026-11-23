import { afterEach, describe, expect, it } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter, Route, Routes, useNavigate } from 'react-router-dom';
import { useUnsavedChangesGuard } from '@/features/academic-structure/components/UnsavedChangesGuard';

function StepPage({ title, nextPath }) {
  const navigate = useNavigate();
  return (
    <>
      <h2>{title}</h2>
      {nextPath && <button onClick={() => navigate(nextPath)}>Avanzar</button>}
    </>
  );
}

function GuardedFlow() {
  const { guardDialog } = useUnsavedChangesGuard(true);
  return (
    <>
      <Routes>
        <Route path="/a" element={<StepPage title="Paso A" nextPath="/b" />} />
        <Route path="/b" element={<StepPage title="Paso B" nextPath="/c" />} />
        <Route path="/c" element={<StepPage title="Paso C" />} />
      </Routes>
      {guardDialog}
    </>
  );
}

function renderFlow() {
  window.history.replaceState(null, '', '/a');
  return render(
    <BrowserRouter>
      <GuardedFlow />
    </BrowserRouter>,
  );
}

async function advanceTo(user, title) {
  await user.click(await screen.findByRole('button', { name: 'Avanzar' }));
  await screen.findByRole('heading', { name: title });
}

async function confirmLeaving(user) {
  const dialog = await screen.findByRole('alertdialog', { name: '¿Salir sin guardar?' });
  await user.click(within(dialog).getByRole('button', { name: 'Salir sin guardar' }));
}

afterEach(() => {
  window.history.replaceState(null, '', '/');
});

describe('back navigation guard on unsaved changes', () => {
  it('asks before leaving with the browser back button and stays when cancelled', async () => {
    const user = userEvent.setup();
    renderFlow();
    await advanceTo(user, 'Paso B');

    window.history.back();

    const dialog = await screen.findByRole('alertdialog', { name: '¿Salir sin guardar?' });
    expect(screen.getByText('Paso B')).toBeInTheDocument();
    await waitFor(() => expect(window.location.pathname).toBe('/b'));

    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(screen.getByText('Paso B')).toBeInTheDocument();
    expect(window.location.pathname).toBe('/b');
  });

  it('completes the original back navigation when the user confirms leaving', async () => {
    const user = userEvent.setup();
    renderFlow();
    await advanceTo(user, 'Paso B');

    window.history.back();
    await confirmLeaving(user);

    expect(await screen.findByRole('heading', { name: 'Paso A' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/a');
  });

  it('restores the exact entry when the user jumped several history entries back', async () => {
    const user = userEvent.setup();
    renderFlow();
    await advanceTo(user, 'Paso B');
    await advanceTo(user, 'Paso C');

    window.history.go(-2);
    await confirmLeaving(user);

    expect(await screen.findByRole('heading', { name: 'Paso A' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/a');
  });
});
