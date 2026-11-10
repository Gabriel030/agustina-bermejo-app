import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Tabs, TabsList, TabsTrigger } from './tabs';

function renderSegmented() {
  return render(
    <Tabs defaultValue="summary">
      <TabsList variant="segmented">
        <TabsTrigger value="summary">Resumen</TabsTrigger>
        <TabsTrigger value="detail">Detalle</TabsTrigger>
      </TabsList>
    </Tabs>,
  );
}

describe('Tabs', () => {
  it('segmented list renders the sliding indicator with token classes', () => {
    const { container } = renderSegmented();
    const indicator = container.querySelector('[aria-hidden="true"]');
    expect(indicator).toHaveClass('bg-(image:--seg-indicator-bg)', 'rounded-(--r-seg-indicator)', 'shadow-(--seg-indicator-shadow)');
  });

  it('active segmented trigger exposes role tab and selected state', () => {
    renderSegmented();
    expect(screen.getByRole('tab', { name: 'Resumen' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: 'Detalle' })).toHaveAttribute('aria-selected', 'false');
  });

  it('segmented list uses the segmented radius token', () => {
    renderSegmented();
    expect(screen.getByRole('tablist')).toHaveClass('rounded-(--r-seg)');
  });
});
