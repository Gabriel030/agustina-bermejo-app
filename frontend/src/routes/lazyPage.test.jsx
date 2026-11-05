import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
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
});
