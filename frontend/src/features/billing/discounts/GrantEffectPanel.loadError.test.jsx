import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { GrantEffectPanel } from './GrantEffectPanel';

describe('GrantEffectPanel load error', () => {
  it('shows the retry state instead of an endless skeleton when the preview fails', async () => {
    const onRetry = vi.fn();
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <GrantEffectPanel values={{}} isReady isLoading={false} isError error={{ status: 500 }} onRetry={onRetry} />
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(onRetry).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('Corregí los problemas marcados para ver qué va a pasar.')).not.toBeInTheDocument();
  });
});
