import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/renderWithProviders';
import { LoadErrorState } from './LoadErrorState';

describe('LoadErrorState', () => {
  it('offers a retry button for a generic failure and calls onRetry', async () => {
    const onRetry = vi.fn();
    renderWithProviders(<LoadErrorState error={{ status: 500 }} onRetry={onRetry} resource="la carrera" />);
    expect(screen.getByText('No pudimos cargar la carrera')).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('makes the retry button a touch target on mobile without changing desktop', () => {
    renderWithProviders(<LoadErrorState error={{ status: 500 }} onRetry={vi.fn()} />);
    const retry = screen.getByRole('button', { name: 'Reintentar' });
    expect(retry).toHaveClass('min-h-11');
    expect(retry).toHaveClass('md:min-h-0');
  });

  it('shows not found with a back link and no retry on 404', () => {
    renderWithProviders(
      <LoadErrorState error={{ status: 404 }} onRetry={vi.fn()} backTo="/volver" backLabel="la lista" />,
    );
    expect(screen.getByText('No encontramos lo que buscabas')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver a la lista' })).toHaveAttribute('href', '/volver');
  });

  it('shows no access and no retry on 403', () => {
    renderWithProviders(<LoadErrorState error={{ status: 403 }} onRetry={vi.fn()} distinguishForbidden />);
    expect(screen.getByText('No tenés acceso a esta pantalla')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
  });

  it('keeps the generic failure with retry on 403 unless forbidden is distinguished', () => {
    renderWithProviders(<LoadErrorState error={{ status: 403 }} onRetry={vi.fn()} resource="la plantilla" />);
    expect(screen.getByText('No pudimos cargar la plantilla')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
