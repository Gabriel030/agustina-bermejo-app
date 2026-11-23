import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Switch } from '@/components/ui/switch';

describe('Switch', () => {
  it('shows the unchecked track with a visible border and a contrasting thumb', () => {
    const { container } = render(<Switch aria-label="Modo oscuro" />);
    const track = screen.getByRole('switch', { name: 'Modo oscuro' });
    const thumb = container.querySelector('[data-slot="switch-thumb"]');

    expect(track).toHaveClass('data-[state=unchecked]:bg-neutral-bg', 'data-[state=unchecked]:border-ink-faint');
    expect(thumb).toHaveClass('data-[state=unchecked]:bg-ink-soft');
    expect(thumb).not.toHaveClass('bg-primary-foreground');
  });

  it('keeps the checked state on the primary track with a light thumb', () => {
    const { container } = render(<Switch aria-label="Notificaciones" defaultChecked />);
    const track = screen.getByRole('switch', { name: 'Notificaciones' });
    const thumb = container.querySelector('[data-slot="switch-thumb"]');

    expect(track).toHaveClass('data-[state=checked]:bg-primary');
    expect(thumb).toHaveClass('data-[state=checked]:bg-primary-foreground');
  });

  it('enlarges the touch target to 44px without changing the visual size', () => {
    render(<Switch aria-label="Recordatorios" />);
    const track = screen.getByRole('switch', { name: 'Recordatorios' });
    const spacingPx = 4;
    const insetYPx = 3.5 * spacingPx;
    const borderPx = 2;
    const visualHeightPx = 20;
    const paddingBoxHeightPx = visualHeightPx - 2 * borderPx;
    const touchHeightPx = paddingBoxHeightPx + 2 * insetYPx;

    expect(track).toHaveClass('h-5', 'w-9', 'after:-inset-y-3.5');
    expect(touchHeightPx).toBe(44);
  });
});
