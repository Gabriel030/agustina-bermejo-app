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
});
