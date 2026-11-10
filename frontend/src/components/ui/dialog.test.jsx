import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from './dialog';

function renderDialog() {
  return render(
    <Dialog open>
      <DialogContent>
        <DialogTitle>Título</DialogTitle>
        <DialogDescription>Descripción</DialogDescription>
      </DialogContent>
    </Dialog>,
  );
}

describe('Dialog', () => {
  it('close button is labelled in Spanish', () => {
    renderDialog();
    expect(screen.getByRole('button', { name: 'Cerrar' })).toBeInTheDocument();
  });

  it('content reads the dialog radius and shadow variables', () => {
    renderDialog();
    expect(screen.getByRole('dialog')).toHaveClass('rounded-dialog', 'shadow-(--sh-dialog)');
  });
});
