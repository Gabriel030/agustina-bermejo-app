import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ErrorBoundary } from '@/components/ErrorBoundary';
import { ErrorFallback } from '@/components/ErrorFallback';
import { setClientErrorReporter } from '@/lib/reportClientError';

function Bomb({ explode }) {
  if (explode) throw new Error('boom');
  return <div>Pantalla sana</div>;
}

function renderBoundary(props) {
  return (
    <ErrorBoundary scope="screen" fallback={(fallbackProps) => <ErrorFallback {...fallbackProps} />} {...props}>
      <Bomb explode={props.explode} />
    </ErrorBoundary>
  );
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    setClientErrorReporter((payload) => console.error('client_error', payload));
  });

  it('rendersTheFallbackWhenAChildThrows', () => {
    render(renderBoundary({ explode: true, resetKey: 'a' }));

    expect(screen.getByText('Algo salió mal')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver al inicio' })).toHaveAttribute('href', '/');
  });

  it('resetsWhenTheResetKeyChanges', () => {
    const { rerender } = render(renderBoundary({ explode: true, resetKey: 'a' }));

    rerender(renderBoundary({ explode: false, resetKey: 'b' }));

    expect(screen.getByText('Pantalla sana')).toBeInTheDocument();
  });

  it('reportsTheErrorToTheClientReporter', () => {
    const reporter = vi.fn();
    setClientErrorReporter(reporter);

    render(renderBoundary({ explode: true, resetKey: 'a' }));

    expect(reporter).toHaveBeenCalledWith(expect.objectContaining({ message: 'boom', scope: 'screen' }));
  });
});
