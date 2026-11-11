import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Button } from './button';

describe('Button', () => {
  it('gradient and primary variants share the same token classes', () => {
    render(
      <>
        <Button variant="gradient">Uno</Button>
        <Button variant="primary">Dos</Button>
      </>,
    );
    ['Uno', 'Dos'].forEach((name) => {
      expect(screen.getByRole('button', { name })).toHaveClass(
        'bg-(image:--btn-primary-bg)',
        'rounded-(--r-btn-primary)',
      );
    });
  });

  it('loading disables the button', () => {
    render(<Button loading>Guardar</Button>);
    expect(screen.getByRole('button', { name: /Guardar/ })).toBeDisabled();
  });

  it('inline and row sizes drop the fixed height and border of the default size', () => {
    render(
      <>
        <Button variant="link" size="inline">Enlace</Button>
        <Button variant="row" size="row">Fila</Button>
      </>,
    );
    expect(screen.getByRole('button', { name: 'Enlace' })).toHaveClass('h-auto', 'p-0', 'border-0');
    expect(screen.getByRole('button', { name: 'Fila' })).toHaveClass('h-auto', 'w-full', 'rounded-2xl');
  });

  it('keeps the native button type when one is passed', () => {
    render(<Button type="button" variant="subtle" size="inline">Volver</Button>);
    expect(screen.getByRole('button', { name: 'Volver' })).toHaveAttribute('type', 'button');
  });
});
