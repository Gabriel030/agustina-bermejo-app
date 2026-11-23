import { beforeEach, describe, expect, it } from 'vitest';
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

function GuardedFlow({ dirty = true }) {
  const { guardDialog } = useUnsavedChangesGuard(dirty);
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

function flow(dirty = true) {
  return (
    <BrowserRouter>
      <GuardedFlow dirty={dirty} />
    </BrowserRouter>
  );
}

function renderFlow(dirty) {
  window.history.replaceState({ idx: 0, key: 'default' }, '', '/a');
  return render(flow(dirty));
}

async function advanceTo(user, title) {
  await user.click(await screen.findByRole('button', { name: 'Avanzar' }));
  await screen.findByRole('heading', { name: title });
}

async function confirmLeaving(user) {
  const dialog = await screen.findByRole('alertdialog', { name: '¿Salir sin guardar?' });
  await user.click(within(dialog).getByRole('button', { name: 'Salir sin guardar' }));
}

beforeEach(() => {
  window.history.replaceState({ idx: 0, key: 'default' }, '', '/');
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
    await waitFor(() => expect(window.location.pathname).toBe('/a'));
  });

  it('restores the exact entry when the user jumped several history entries back', async () => {
    const user = userEvent.setup();
    renderFlow();
    await advanceTo(user, 'Paso B');
    await advanceTo(user, 'Paso C');

    window.history.go(-2);
    await confirmLeaving(user);

    expect(await screen.findByRole('heading', { name: 'Paso A' })).toBeInTheDocument();
    await waitFor(() => expect(window.location.pathname).toBe('/a'));
  });

  it('does not let two consecutive back presses leave without the dialog', async () => {
    const user = userEvent.setup();
    renderFlow();
    await advanceTo(user, 'Paso B');
    await advanceTo(user, 'Paso C');

    window.history.back();
    window.history.back();

    await screen.findByRole('alertdialog', { name: '¿Salir sin guardar?' });
    await waitFor(() => expect(window.location.pathname).toBe('/c'));
    expect(screen.getByText('Paso C')).toBeInTheDocument();
  });

  it('asks before going forward again into a page with unsaved changes', async () => {
    const user = userEvent.setup();
    renderFlow();
    await advanceTo(user, 'Paso B');
    window.history.back();
    await confirmLeaving(user);
    await screen.findByRole('heading', { name: 'Paso A' });

    window.history.forward();

    await screen.findByRole('alertdialog', { name: '¿Salir sin guardar?' });
    await waitFor(() => expect(window.location.pathname).toBe('/a'));
    await confirmLeaving(user);

    expect(await screen.findByRole('heading', { name: 'Paso B' })).toBeInTheDocument();
    await waitFor(() => expect(window.location.pathname).toBe('/b'));
  });

  it('leaves without asking when the history entry has no index from the router', async () => {
    const user = userEvent.setup();
    renderFlow();
    window.history.pushState(null, '', '/fuera');
    await advanceTo(user, 'Paso B');

    window.history.back();

    await waitFor(() => expect(window.location.pathname).toBe('/fuera'));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('closes the dialog and stops intercepting when the guard becomes inactive', async () => {
    const user = userEvent.setup();
    const view = renderFlow();
    await advanceTo(user, 'Paso B');
    window.history.back();
    await screen.findByRole('alertdialog', { name: '¿Salir sin guardar?' });

    view.rerender(flow(false));

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    window.history.back();

    await waitFor(() => expect(window.location.pathname).toBe('/a'));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(screen.getByText('Paso A')).toBeInTheDocument();
  });
});
