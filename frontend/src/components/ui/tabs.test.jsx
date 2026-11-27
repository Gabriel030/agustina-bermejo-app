import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Tabs, TabsList, TabsTrigger } from './tabs';

function renderLine() {
  return render(
    <Tabs defaultValue="summary">
      <TabsList variant="line">
        <TabsTrigger value="summary">Resumen</TabsTrigger>
        <TabsTrigger value="detail">Detalle</TabsTrigger>
      </TabsList>
    </Tabs>,
  );
}

describe('Tabs', () => {
  it('line list is a bordered strip with no filled background', () => {
    renderLine();
    expect(screen.getByRole('tablist')).toHaveClass('border-b', 'border-border', 'bg-transparent', 'rounded-none');
  });

  it('active line trigger uses the accent underline like the top bar', () => {
    renderLine();
    expect(screen.getByRole('tab', { name: 'Resumen' })).toHaveClass('data-active:border-b-(--accent)', 'data-active:text-(--accent)', 'hover:bg-neutral-bg');
  });

  it('active line trigger exposes role tab and selected state', () => {
    renderLine();
    expect(screen.getByRole('tab', { name: 'Resumen' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: 'Detalle' })).toHaveAttribute('aria-selected', 'false');
  });
});
