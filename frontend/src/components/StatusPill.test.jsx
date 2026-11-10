import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { StatusPill } from './StatusPill';

describe('StatusPill', () => {
  it('warning tone uses the readable pill foreground token', () => {
    render(<StatusPill tone="warning">Pendiente</StatusPill>);
    const pill = screen.getByText('Pendiente');
    expect(pill).toHaveClass('text-pill-warning');
    expect(pill).not.toHaveClass('text-warning');
  });

  it('dot is rendered only when requested', () => {
    const { container, rerender } = render(<StatusPill tone="success">Activo</StatusPill>);
    expect(container.querySelectorAll('span span')).toHaveLength(0);
    rerender(
      <StatusPill tone="success" dot>
        Activo
      </StatusPill>,
    );
    expect(container.querySelectorAll('span span')).toHaveLength(1);
  });
});
