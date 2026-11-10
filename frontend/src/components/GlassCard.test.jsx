import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { GlassCard } from './GlassCard';

describe('GlassCard', () => {
  it('uses card radius and surface tokens', () => {
    render(<GlassCard data-testid="card" />);
    expect(screen.getByTestId('card')).toHaveClass('rounded-card', 'bg-surface', 'shadow-card');
  });

  it('interactive variant adds the lift hover class', () => {
    render(<GlassCard data-testid="card" interactive />);
    expect(screen.getByTestId('card')).toHaveClass('hover:translate-y-(--card-lift-y)');
  });

  it('does not set an inline box shadow', () => {
    render(<GlassCard data-testid="card" />);
    expect(screen.getByTestId('card').style.boxShadow).toBe('');
  });
});
