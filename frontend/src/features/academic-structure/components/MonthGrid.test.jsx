import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MonthGrid } from './MonthGrid';

const ENTRIES = {
  '2026-11-25': { label: 'Navidad adelantada', typeLabel: 'Nacional', chip: 'N', cellClass: 'bg-accent-soft' },
};

function Harness({ onSelect = () => {}, initialYear = 2026, initialMonth = 10, selectedIso = null }) {
  const [view, setView] = useState({ year: initialYear, month: initialMonth });
  return (
    <MonthGrid
      year={view.year}
      month={view.month}
      entries={ENTRIES}
      todayIso="2026-11-05"
      selectedIso={selectedIso}
      onSelect={onSelect}
      onNavigate={(year, month) => setView({ year, month })}
    />
  );
}

describe('MonthGrid', () => {
  it('exposes a grid with a single tab stop', () => {
    render(<Harness />);
    expect(screen.getByRole('grid')).toBeInTheDocument();
    const stops = screen.getAllByRole('button').filter((button) => button.tabIndex === 0);
    expect(stops).toHaveLength(1);
  });

  it('describes a day with holiday and an empty day', () => {
    render(<Harness />);
    expect(screen.getByRole('button', { name: '25 de noviembre: Navidad adelantada, Nacional' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '3 de noviembre, sin feriado' })).toBeInTheDocument();
  });

  it('selects the day on click', async () => {
    const onSelect = vi.fn();
    render(<Harness onSelect={onSelect} />);
    await userEvent.click(screen.getByRole('button', { name: '3 de noviembre, sin feriado' }));
    expect(onSelect).toHaveBeenCalledWith('2026-11-03');
  });

  it('moves focus with arrow keys and week jumps', async () => {
    render(<Harness />);
    await userEvent.click(screen.getByRole('button', { name: '10 de noviembre, sin feriado' }));
    await userEvent.keyboard('{ArrowRight}');
    expect(screen.getByRole('button', { name: '11 de noviembre, sin feriado' })).toHaveFocus();
    await userEvent.keyboard('{ArrowDown}');
    expect(screen.getByRole('button', { name: '18 de noviembre, sin feriado' })).toHaveFocus();
  });

  it('jumps to the start and end of the week with Home and End', async () => {
    render(<Harness />);
    await userEvent.click(screen.getByRole('button', { name: '11 de noviembre, sin feriado' }));
    await userEvent.keyboard('{Home}');
    expect(screen.getByRole('button', { name: '9 de noviembre, sin feriado' })).toHaveFocus();
    await userEvent.keyboard('{End}');
    expect(screen.getByRole('button', { name: '15 de noviembre, sin feriado' })).toHaveFocus();
  });

  it('changes month with PageDown and PageUp', async () => {
    render(<Harness />);
    await userEvent.click(screen.getByRole('button', { name: '10 de noviembre, sin feriado' }));
    await userEvent.keyboard('{PageDown}');
    expect(screen.getByRole('button', { name: '10 de diciembre, sin feriado' })).toHaveFocus();
    await userEvent.keyboard('{PageUp}{PageUp}');
    expect(screen.getByRole('button', { name: '10 de octubre, sin feriado' })).toHaveFocus();
  });

  it('crosses to the next month when moving past the last day', async () => {
    render(<Harness />);
    await userEvent.click(screen.getByRole('button', { name: '30 de noviembre, sin feriado' }));
    await userEvent.keyboard('{ArrowRight}');
    expect(screen.getByRole('button', { name: '1 de diciembre, sin feriado' })).toHaveFocus();
  });

  it('clamps PageDown to the last day of a shorter month', async () => {
    render(<Harness initialYear={2027} initialMonth={0} />);
    await userEvent.click(screen.getByRole('button', { name: '31 de enero, sin feriado' }));
    await userEvent.keyboard('{PageDown}');
    expect(screen.getByRole('button', { name: '28 de febrero, sin feriado' })).toHaveFocus();
  });

  it('marks the selected day', () => {
    render(<Harness selectedIso="2026-11-03" />);
    const cell = screen.getByRole('button', { name: '3 de noviembre, sin feriado' }).closest('[role="gridcell"]');
    expect(cell).toHaveAttribute('aria-selected', 'true');
  });
});
