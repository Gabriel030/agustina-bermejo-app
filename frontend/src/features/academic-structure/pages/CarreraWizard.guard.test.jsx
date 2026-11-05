import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Routes, Route } from 'react-router-dom';
import { renderWithProviders } from '@/test/renderWithProviders';
import { axe } from '@/test/axe';
import { CarreraWizard } from './CarreraWizard';

vi.setConfig({ testTimeout: 20000 });

function mount() {
  return renderWithProviders(
    <Routes>
      <Route path="/administrativo/gestion-academica/carreras/nueva" element={<CarreraWizard />} />
      <Route path="/administrativo/gestion-academica" element={<div>Banco</div>} />
    </Routes>,
    { route: '/administrativo/gestion-academica/carreras/nueva' },
  );
}

describe('CarreraWizard — stepper and exit protection', () => {
  it('blocks jumping to the next step through the stepper when the name was cleared', async () => {
    const user = userEvent.setup();
    mount();

    await user.type(screen.getByPlaceholderText('Ej. Licenciatura en Enfermería'), 'Enfermería');
    await user.type(screen.getByPlaceholderText('Ej. 3'), '3');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await user.click(await screen.findByRole('button', { name: 'Datos básicos' }));
    await user.clear(screen.getByPlaceholderText('Ej. Licenciatura en Enfermería'));
    await user.click(screen.getByRole('button', { name: 'Contenido académico' }));

    expect(screen.getByRole('button', { name: 'Datos básicos' })).toHaveAttribute('aria-current', 'step');
    expect(screen.getByText('El nombre es obligatorio.')).toBeInTheDocument();
  });

  it('does not ask for confirmation when nothing was typed', async () => {
    const user = userEvent.setup();
    mount();

    await user.click(screen.getByRole('link', { name: /Carreras/ }));

    expect(await screen.findByText('Banco')).toBeInTheDocument();
  });

  it('asks before leaving with unsaved data, stays on cancel and leaves on confirm', async () => {
    const user = userEvent.setup();
    mount();

    await user.type(screen.getByPlaceholderText('Ej. Licenciatura en Enfermería'), 'Enfermería');
    await user.click(screen.getByRole('link', { name: /Carreras/ }));

    expect(await screen.findByRole('alertdialog', { name: '¿Salir sin guardar?' })).toBeInTheDocument();
    expect(await axe(document.body)).toHaveNoViolations();

    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText('Ej. Licenciatura en Enfermería')).toHaveValue('Enfermería');

    await user.click(screen.getByRole('link', { name: /Carreras/ }));
    await user.click(await screen.findByRole('button', { name: 'Salir sin guardar' }));
    expect(await screen.findByText('Banco')).toBeInTheDocument();
  });

  it('registers the beforeunload protection only while there are unsaved changes', async () => {
    const user = userEvent.setup();
    mount();
    const clean = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(clean);
    expect(clean.defaultPrevented).toBe(false);

    await user.type(screen.getByPlaceholderText('Ej. Licenciatura en Enfermería'), 'E');
    const dirty = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(dirty);
    expect(dirty.defaultPrevented).toBe(true);
  });
});
