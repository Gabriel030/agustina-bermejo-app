import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { KpiFilterChip } from './KpiFilterChip';

describe('KpiFilterChip', () => {
  it('exposesTheCountAndLabelAsAToggleButtonWithItsPressedState', () => {
    render(<KpiFilterChip label="Cerradas" count={4} pressed onClick={() => {}} />);

    const chip = screen.getByRole('button', { name: '4 Cerradas' });
    expect(chip).toHaveAttribute('aria-pressed', 'true');
  });

  it('reportsNotPressedWhenTheFilterIsInactive', () => {
    render(<KpiFilterChip label="Cerradas" count={4} pressed={false} onClick={() => {}} />);

    expect(screen.getByRole('button', { name: '4 Cerradas' })).toHaveAttribute('aria-pressed', 'false');
  });

  it('callsOnClickWhenChosen', async () => {
    const onClick = vi.fn();
    render(<KpiFilterChip label="Cerradas" count={4} pressed={false} onClick={onClick} />);

    await userEvent.click(screen.getByRole('button', { name: '4 Cerradas' }));

    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('rendersTheInlineVariantWithItsOwnAccessibleName', () => {
    render(<KpiFilterChip variant="inline" label="Aprobados" count={7} pressed ariaLabel="Aprobados: 7. Filtrar la planilla" onClick={() => {}} />);

    const chip = screen.getByRole('button', { name: 'Aprobados: 7. Filtrar la planilla' });
    expect(chip).toHaveAttribute('aria-pressed', 'true');
    expect(chip).toHaveClass('ring-2', 'ring-primary');
    expect(chip).not.toHaveClass('bg-accent-soft');
  });

  it('highlightsThePressedCardVariantWithTheAccentSurface', () => {
    render(<KpiFilterChip label="Cerradas" count={4} pressed onClick={() => {}} />);

    expect(screen.getByRole('button', { name: '4 Cerradas' })).toHaveClass('bg-accent-soft', 'border-primary');
  });
});
