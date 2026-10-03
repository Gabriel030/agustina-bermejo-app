import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/renderWithProviders';
import { CarreraWizard } from './CarreraWizard';

describe('CarreraWizard', () => {
  it('marks the current step and lets the user go back to a visited step', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CarreraWizard />);

    expect(screen.getByRole('button', { name: 'Datos básicos' })).toHaveAttribute('aria-current', 'step');

    await user.type(screen.getByPlaceholderText('Ej. Licenciatura en Enfermería'), 'Enfermería');
    await user.type(screen.getByPlaceholderText('Ej. 3'), '3');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByRole('button', { name: 'Contenido académico' })).toHaveAttribute('aria-current', 'step');
    await user.click(screen.getByRole('button', { name: 'Datos básicos' }));

    expect(screen.getByPlaceholderText('Ej. Licenciatura en Enfermería')).toHaveValue('Enfermería');
  });
});
