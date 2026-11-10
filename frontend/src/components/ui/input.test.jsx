import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Input } from './input';
import { Textarea } from './textarea';

describe('Input', () => {
  it('uses the control radius and control background tokens', () => {
    render(<Input aria-label="Nombre" />);
    expect(screen.getByLabelText('Nombre')).toHaveClass('rounded-control', 'bg-control');
  });

  it('textarea uses the same control tokens', () => {
    render(<Textarea aria-label="Notas" />);
    expect(screen.getByLabelText('Notas')).toHaveClass('rounded-control', 'bg-control');
  });
});
