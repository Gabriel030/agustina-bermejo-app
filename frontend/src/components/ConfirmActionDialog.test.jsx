import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from '@/test/axe';
import ConfirmActionDialog from './ConfirmActionDialog';

function renderDialog(props = {}) {
  const onConfirm = vi.fn();
  const onOpenChange = vi.fn();
  render(
    <ConfirmActionDialog
      open
      onOpenChange={onOpenChange}
      title="Desactivar curso"
      description="El curso va a quedar inactivo."
      confirmLabel="Desactivar"
      onConfirm={onConfirm}
      {...props}
    />,
  );
  return { onConfirm, onOpenChange };
}

describe('ConfirmActionDialog', () => {
  it('exposes an alertdialog named by its title and described by its text', () => {
    renderDialog();
    const dialog = screen.getByRole('alertdialog', { name: 'Desactivar curso' });
    expect(dialog).toHaveAccessibleDescription('El curso va a quedar inactivo.');
  });

  it('moves the focus to the cancel button, the safe option', () => {
    renderDialog();
    expect(screen.getByRole('button', { name: 'Cancelar' })).toHaveFocus();
  });

  it('closes on Escape without confirming', async () => {
    const { onConfirm, onOpenChange } = renderDialog();
    await userEvent.setup().keyboard('{Escape}');
    expect(onOpenChange).toHaveBeenCalledWith(false);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('cancel does not confirm and confirm does', async () => {
    const user = userEvent.setup();
    const { onConfirm, onOpenChange } = renderDialog();
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(onConfirm).not.toHaveBeenCalled();
    expect(onOpenChange).toHaveBeenCalledWith(false);
    await user.click(screen.getByRole('button', { name: 'Desactivar' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('ignores Escape while the action is pending', async () => {
    const { onOpenChange } = renderDialog({ pending: true });
    await userEvent.setup().keyboard('{Escape}');
    expect(onOpenChange).not.toHaveBeenCalled();
  });

  it('has no accessibility violations', async () => {
    renderDialog();
    expect(await axe(document.body)).toHaveNoViolations();
  });
});
