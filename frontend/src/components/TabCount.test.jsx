import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TabCount } from '@/components/TabCount';

describe('TabCount', () => {
  it('renders the count as a chip', () => {
    render(<TabCount count={3} />);

    expect(screen.getByText('3')).toHaveAttribute('data-slot', 'tab-count');
  });

  it('renders nothing when the count is zero or missing', () => {
    const { container, rerender } = render(<TabCount count={0} />);
    expect(container).toBeEmptyDOMElement();

    rerender(<TabCount />);
    expect(container).toBeEmptyDOMElement();
  });

  it('switches to the on-gradient colors when its tab is active', () => {
    render(<TabCount count={2} />);

    expect(screen.getByText('2').className).toContain('[[data-state=active]_&]:bg-white/25');
  });
});
