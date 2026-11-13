import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/renderWithProviders';
import { CarreraWizard } from './CarreraWizard';

const createProgram = vi.hoisted(() => ({ mutateAsync: vi.fn(), isPending: false }));

vi.mock('@/features/academic-structure/hooks/usePrograms', () => ({
  useCreateProgram: () => createProgram,
}));
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

describe('CarreraWizard create body', () => {
  it('sends the monthly fee and the enrollment fee in pesos when creating the program', async () => {
    createProgram.mutateAsync.mockResolvedValue({ id: 1 });
    const user = userEvent.setup();
    renderWithProviders(<CarreraWizard />);
    await user.type(screen.getByPlaceholderText('Ej. Licenciatura en Enfermería'), 'Enfermería');
    await user.type(screen.getByPlaceholderText('Ej. 3'), '3');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await user.type(await screen.findByPlaceholderText('Ej. 180'), '180');
    await user.type(screen.getByPlaceholderText('Ej. 48000'), '48000');
    await user.type(screen.getByPlaceholderText('Ej. 95000'), '95000');

    await user.click(screen.getByRole('button', { name: 'Crear carrera' }));

    await vi.waitFor(() => expect(createProgram.mutateAsync).toHaveBeenCalled());
    const body = createProgram.mutateAsync.mock.calls[0][0];
    expect(body.price).toEqual({ amount: '48000', currency: 'ARS' });
    expect(body.enrollmentFeeAmount).toEqual({ amount: '95000', currency: 'ARS' });
  });
});
