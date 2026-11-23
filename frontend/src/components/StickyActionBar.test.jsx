import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Undo2 } from 'lucide-react';
import { StickyActionBar } from '@/components/StickyActionBar';
import { Button } from '@/components/ui/button';
import { axe } from '@/test/axe';
import { restoreViewport, useCompactViewport } from '@/test/compactViewport';

afterEach(restoreViewport);

function renderMenuBar(overrides = {}) {
  const onDiscard = vi.fn();
  const onSave = vi.fn();
  const utils = render(
    <StickyActionBar
      compact="menu"
      aria-label="Cambios sin guardar"
      summary="3 cambios sin guardar"
      hint="Quedan 4 sin marcar"
      primary={{ label: 'Guardar', onClick: onSave }}
      secondary={[{ key: 'discard', label: 'Descartar', icon: Undo2, destructive: true, onSelect: onDiscard }]}
      {...overrides}
    />,
  );
  return { ...utils, onDiscard, onSave };
}

describe('StickyActionBar', () => {
  it('is a labelled region by default', () => {
    render(<StickyActionBar summary="$ 10,00">contenido</StickyActionBar>);
    expect(screen.getByRole('region', { name: 'Acciones de la página' })).toHaveTextContent('$ 10,00');
  });

  it('accepts a custom label', () => {
    render(<StickyActionBar aria-label="Cambios sin guardar" summary="x" />);
    expect(screen.getByRole('region', { name: 'Cambios sin guardar' })).toBeInTheDocument();
  });

  it('renders the children in a row on every viewport in row mode', () => {
    useCompactViewport();
    render(
      <StickyActionBar summary="Total" hint="Total de la nota">
        <Button>Volver</Button>
        <Button>Continuar</Button>
      </StickyActionBar>,
    );
    const region = screen.getByRole('region');
    expect(within(region).getByRole('button', { name: 'Volver' })).toBeInTheDocument();
    expect(within(region).getByRole('button', { name: 'Continuar' })).toBeInTheDocument();
    expect(within(region).queryByRole('button', { name: 'Más acciones' })).not.toBeInTheDocument();
  });

  describe('menu mode on desktop', () => {
    it('shows the secondary action as a button next to the primary one', async () => {
      const user = userEvent.setup();
      const { onDiscard, onSave } = renderMenuBar();
      expect(screen.queryByRole('button', { name: 'Más acciones' })).not.toBeInTheDocument();
      await user.click(screen.getByRole('button', { name: 'Descartar' }));
      await user.click(screen.getByRole('button', { name: 'Guardar' }));
      expect(onDiscard).toHaveBeenCalledTimes(1);
      expect(onSave).toHaveBeenCalledTimes(1);
      expect(screen.getByText('3 cambios sin guardar')).toBeInTheDocument();
    });
  });

  describe('menu mode on a compact viewport', () => {
    it('moves the secondary action into the more menu and fires it', async () => {
      useCompactViewport();
      const user = userEvent.setup();
      const { onDiscard } = renderMenuBar();
      expect(screen.queryByRole('button', { name: 'Descartar' })).not.toBeInTheDocument();
      const more = screen.getByRole('button', { name: 'Más acciones' });
      expect(more).toHaveAttribute('aria-haspopup', 'menu');
      await user.click(more);
      expect(more).toHaveAttribute('aria-expanded', 'true');
      await user.click(await screen.findByRole('menuitem', { name: 'Descartar' }));
      expect(onDiscard).toHaveBeenCalledTimes(1);
      expect(screen.queryByRole('menuitem', { name: 'Descartar' })).not.toBeInTheDocument();
    });

    it('closes the menu with Escape and returns the focus to the more button', async () => {
      useCompactViewport();
      const user = userEvent.setup();
      renderMenuBar();
      const more = screen.getByRole('button', { name: 'Más acciones' });
      await user.click(more);
      await screen.findByRole('menuitem', { name: 'Descartar' });
      await user.keyboard('{Escape}');
      expect(screen.queryByRole('menuitem', { name: 'Descartar' })).not.toBeInTheDocument();
      expect(more).toHaveFocus();
    });

    it('does not fire a disabled secondary action', async () => {
      useCompactViewport();
      const user = userEvent.setup();
      const { onDiscard } = renderMenuBar({
        secondary: [{ key: 'discard', label: 'Descartar', onSelect: vi.fn(), disabled: true }],
      });
      await user.click(screen.getByRole('button', { name: 'Más acciones' }));
      const item = await screen.findByRole('menuitem', { name: 'Descartar' });
      expect(item).toHaveAttribute('aria-disabled', 'true');
      await user.click(item);
      expect(onDiscard).not.toHaveBeenCalled();
    });

    it('shows the short texts but keeps the full ones for assistive technology', () => {
      useCompactViewport();
      renderMenuBar({ compactSummary: '3 cambios', compactHint: 'Quedan 4 sin marcar' });
      expect(screen.getByText('3 cambios')).toHaveAttribute('class', expect.stringContaining('truncate'));
      expect(screen.getByText('3 cambios sin guardar')).toBeInTheDocument();
      expect(screen.getByText('3 cambios').closest('[aria-hidden="true"]')).not.toBeNull();
    });

    it('announces the saving state and shows the spinner', () => {
      useCompactViewport();
      renderMenuBar({ primary: { label: 'Guardar', loading: true, onClick: vi.fn() } });
      const region = screen.getByRole('region', { name: 'Cambios sin guardar' });
      expect(region).toHaveAttribute('aria-busy', 'true');
      const saving = within(region).getByRole('button', { name: 'Guardando…' });
      expect(saving).toBeDisabled();
      expect(saving.querySelector('svg.animate-spin')).not.toBeNull();
      expect(region.querySelector('[aria-live="polite"]')).toHaveTextContent('Guardando…');
    });

    it('links the disabled primary action to the visible reason', () => {
      useCompactViewport();
      renderMenuBar({ hint: 'Cambiá un valor para habilitar Guardar', primary: { label: 'Guardar', 'aria-disabled': 'true', onClick: vi.fn() } });
      const primary = screen.getByRole('button', { name: 'Guardar' });
      expect(primary).toHaveAccessibleDescription('Cambiá un valor para habilitar Guardar');
    });

    it('has no accessibility violations with the menu open', async () => {
      useCompactViewport();
      const user = userEvent.setup();
      const { container } = renderMenuBar();
      await user.click(screen.getByRole('button', { name: 'Más acciones' }));
      await screen.findByRole('menuitem', { name: 'Descartar' });
      expect(await axe(container)).toHaveNoViolations();
    });
  });

  it('announces the saving state in row mode too', () => {
    render(
      <StickyActionBar summary="x" busy>
        <Button>Ok</Button>
      </StickyActionBar>,
    );
    expect(screen.getByRole('region')).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByText('Guardando…')).toBeInTheDocument();
  });

  it('has no accessibility violations on desktop', async () => {
    const { container } = renderMenuBar();
    expect(await axe(container)).toHaveNoViolations();
  });
});
