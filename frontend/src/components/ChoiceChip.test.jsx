import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ChoiceChip } from './ChoiceChip';

describe('ChoiceChip', () => {
  it('exposesItsSelectedStateAsAToggleButton', () => {
    render(<ChoiceChip selected onClick={() => {}}>Actuales</ChoiceChip>);

    expect(screen.getByRole('button', { name: 'Actuales' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('callsOnClickWhenChosen', async () => {
    const onClick = vi.fn();
    render(<ChoiceChip selected={false} onClick={onClick}>Anteriores</ChoiceChip>);

    await userEvent.click(screen.getByRole('button', { name: 'Anteriores' }));

    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('highlightsOnlyTheSelectedChipWithTheSoftTone', () => {
    render(
      <>
        <ChoiceChip selected onClick={() => {}}>Uno</ChoiceChip>
        <ChoiceChip selected={false} onClick={() => {}}>Dos</ChoiceChip>
      </>,
    );

    expect(screen.getByRole('button', { name: 'Uno' })).toHaveClass('bg-accent-soft', 'text-brand', 'font-medium');
    expect(screen.getByRole('button', { name: 'Dos' })).not.toHaveClass('bg-accent-soft');
    expect(screen.getByRole('button', { name: 'Dos' })).toHaveClass('bg-neutral-bg/60');
  });

  it('usesTheSolidToneAndPillShapeWhenAsked', () => {
    render(<ChoiceChip selected shape="pill" size="md" tone="solid" onClick={() => {}}>Mañana</ChoiceChip>);

    expect(screen.getByRole('button', { name: 'Mañana' })).toHaveClass('bg-accent', 'text-on-accent', 'rounded-full', 'px-4', 'py-1.5');
  });

  it('keepsTheTextToneWithoutBackgroundWhenIdle', () => {
    render(<ChoiceChip tone="text" selected={false} onClick={() => {}}>Anteriores</ChoiceChip>);

    const chip = screen.getByRole('button', { name: 'Anteriores' });
    expect(chip).toHaveClass('text-ink-faint');
    expect(chip).not.toHaveClass('bg-neutral-bg/60');
  });

  it('keepsMediumWeightWhenStrongEvenIfIdle', () => {
    render(<ChoiceChip strong selected={false} onClick={() => {}}>Curso</ChoiceChip>);

    expect(screen.getByRole('button', { name: 'Curso' })).toHaveClass('font-medium');
  });
});
