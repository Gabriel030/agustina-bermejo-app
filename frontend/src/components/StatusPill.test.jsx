import { render, screen } from '@testing-library/react';
import { Check } from 'lucide-react';
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

  it('renders a decorative icon before the text only when given', () => {
    const { container, rerender } = render(<StatusPill tone="success">Activo</StatusPill>);
    expect(container.querySelector('svg')).toBeNull();
    rerender(
      <StatusPill tone="success" icon={Check}>
        Activo
      </StatusPill>,
    );
    const icon = container.querySelector('svg');
    expect(icon).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByText('Activo')).toBeInTheDocument();
  });

  it('warning tone shows an alert icon by default', () => {
    const { container } = render(<StatusPill tone="warning">Pendiente</StatusPill>);
    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    expect(container.querySelector('svg')).toHaveClass('lucide-triangle-alert');
  });

  it('danger tone shows a circle with x icon by default', () => {
    const { container } = render(<StatusPill tone="danger">Vencido</StatusPill>);
    expect(container.querySelector('svg')).toHaveClass('lucide-circle-x');
  });

  it.each(['success', 'accent', 'neutral'])('%s tone has no automatic icon', (tone) => {
    const { container } = render(<StatusPill tone={tone}>Estado</StatusPill>);
    expect(container.querySelector('svg')).toBeNull();
  });

  it('explicit icon wins over the tone default', () => {
    const { container } = render(
      <StatusPill tone="danger" icon={Check}>
        Vencido
      </StatusPill>,
    );
    expect(container.querySelector('svg')).toHaveClass('lucide-check');
    expect(container.querySelectorAll('svg')).toHaveLength(1);
  });

  it('null icon turns the automatic icon off', () => {
    const { container } = render(
      <StatusPill tone="warning" icon={null}>
        Pendiente
      </StatusPill>,
    );
    expect(container.querySelector('svg')).toBeNull();
  });

  it('dot suppresses the automatic icon', () => {
    const { container } = render(
      <StatusPill tone="warning" dot>
        Pendiente
      </StatusPill>,
    );
    expect(container.querySelector('svg')).toBeNull();
  });
});
