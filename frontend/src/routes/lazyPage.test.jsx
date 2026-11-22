import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { ErrorBoundary } from '@/components/ErrorBoundary';
import { lazyPage } from '@/routes/lazyPage';

describe('lazyPage', () => {
  it('rendersFallbackThenPage', async () => {
    const Page = lazyPage(() => Promise.resolve({ Demo: () => <p>demo</p> }), 'Demo');

    const { container } = render(<Page />);

    expect(container.querySelector('.animate-spin')).not.toBeNull();
    expect(screen.queryByText('demo')).toBeNull();
    expect(await screen.findByText('demo')).toBeTruthy();
  });

  it('passesPropsToThePage', async () => {
    const Page = lazyPage(() => Promise.resolve({ Demo: ({ label }) => <p>{label}</p> }), 'Demo');

    render(<Page label="hola" />);

    expect(await screen.findByText('hola')).toBeTruthy();
  });

  it('loadsThePageAfterTheBoundaryResetsFollowingAFailedChunk', async () => {
    const loader = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('Failed to fetch dynamically imported module'))
      .mockResolvedValue({ Demo: () => <p>demo</p> });
    const Page = lazyPage(loader, 'Demo');
    vi.spyOn(console, 'error').mockImplementation(() => {});

    render(
      <ErrorBoundary scope="test" fallback={({ reset }) => <button onClick={reset}>retry</button>}>
        <Page />
      </ErrorBoundary>,
    );
    fireEvent.click(await screen.findByText('retry'));

    expect(await screen.findByText('demo')).toBeTruthy();
    expect(loader).toHaveBeenCalledTimes(2);
  });
});
