import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ErrorFallback } from '@/components/ErrorFallback';
import { isChunkLoadError, reloadOnceForChunkError } from '@/lib/chunkLoadError';

describe('isChunkLoadError', () => {
  it.each([
    'Failed to fetch dynamically imported module: https://app/assets/Page-abc.js',
    'error loading dynamically imported module',
    'Importing a module script failed.',
    'Loading chunk 42 failed.',
  ])('recognizesTheBrowserMessage %s', (message) => {
    expect(isChunkLoadError(new Error(message))).toBe(true);
  });

  it('recognizesAnErrorNamedChunkLoadError', () => {
    const error = new Error('x');
    error.name = 'ChunkLoadError';
    expect(isChunkLoadError(error)).toBe(true);
  });

  it('ignoresOrdinaryErrors', () => {
    expect(isChunkLoadError(new Error('Cannot read properties of undefined'))).toBe(false);
    expect(isChunkLoadError(null)).toBe(false);
  });
});

describe('reloadOnceForChunkError', () => {
  afterEach(() => {
    sessionStorage.clear();
  });

  it('reloadsOnlyOnceWithinTheGuardWindow', () => {
    const reload = vi.fn();
    expect(reloadOnceForChunkError({ reload, now: 1000 })).toBe(true);
    expect(reloadOnceForChunkError({ reload, now: 2000 })).toBe(false);
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('allowsAnotherReloadAfterTheGuardWindowPasses', () => {
    const reload = vi.fn();
    reloadOnceForChunkError({ reload, now: 1000 });
    expect(reloadOnceForChunkError({ reload, now: 1000 + 61000 })).toBe(true);
    expect(reload).toHaveBeenCalledTimes(2);
  });
});

describe('ErrorFallback with a chunk error', () => {
  afterEach(() => {
    sessionStorage.clear();
    vi.unstubAllGlobals();
  });

  it('offersAReloadButtonAndExplainsWhenReloadingDidNotHelp', async () => {
    const reload = vi.fn();
    vi.stubGlobal('location', { ...window.location, reload });
    const user = userEvent.setup();
    render(<ErrorFallback error={new Error('Failed to fetch dynamically imported module: /a.js')} reset={() => {}} />);

    await user.click(screen.getByRole('button', { name: 'Recargar' }));
    expect(reload).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('button', { name: 'Recargar' }));
    expect(reload).toHaveBeenCalledTimes(1);
    expect(screen.getByText(/Ya intentamos recargar/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Recargar' })).not.toBeInTheDocument();
  });

  it('keepsTheGenericFallbackForOrdinaryErrors', () => {
    render(<ErrorFallback error={new Error('boom')} reset={() => {}} />);

    expect(screen.getByText('Algo salió mal')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Recargar' })).not.toBeInTheDocument();
  });
});
