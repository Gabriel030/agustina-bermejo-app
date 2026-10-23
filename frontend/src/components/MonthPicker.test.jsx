import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MonthPicker } from '@/components/MonthPicker';

function setup(props = {}) {
  const onChange = vi.fn();
  render(<MonthPicker value="2026-10" onChange={onChange} minMonth="2025-03" maxMonth="2026-10" currentMonth="2026-10" {...props} />);
  return onChange;
}

const trigger = () => screen.getByRole('button', { name: /^Mes, / });
const open = () => userEvent.click(trigger());
const cell = (name) => screen.getByRole('button', { name });

describe('MonthPicker', () => {
  it('shows the long month on the trigger and applies the chosen month closing the popover', async () => {
    const onChange = setup();
    expect(trigger()).toHaveTextContent('octubre 2026');

    await open();
    await userEvent.click(cell('mayo 2026'));

    expect(onChange).toHaveBeenCalledWith('2026-05');
    await waitFor(() => expect(screen.queryByRole('group', { name: 'Meses de 2026' })).not.toBeInTheDocument());
  });

  it('marks the selected month and the current month', async () => {
    setup({ value: '2026-05' });
    await open();

    expect(cell('mayo 2026')).toHaveAttribute('aria-pressed', 'true');
    expect(cell('octubre 2026')).toHaveAttribute('aria-current', 'date');
    expect(cell('octubre 2026')).toHaveAttribute('aria-pressed', 'false');
  });

  it('crosses the year with the arrows and picks a month of the previous year', async () => {
    const onChange = setup();
    await open();

    await userEvent.click(screen.getByRole('button', { name: 'Año anterior' }));
    expect(screen.getByRole('group', { name: 'Meses de 2025' })).toBeInTheDocument();
    await userEvent.click(cell('abril 2025'));

    expect(onChange).toHaveBeenCalledWith('2025-04');
  });

  it('disables the months before the first month with data and after the current one, with the reason in the label', async () => {
    const onChange = setup();
    await open();
    await userEvent.click(screen.getByRole('button', { name: 'Año anterior' }));

    const before = cell('febrero 2025, sin datos de este mes');
    expect(before).toHaveAttribute('aria-disabled', 'true');
    await userEvent.click(before);
    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Año anterior, sin datos de este año' })).toHaveAttribute('aria-disabled', 'true');

    await userEvent.click(screen.getByRole('button', { name: 'Año siguiente' }));
    expect(screen.getByRole('button', { name: 'noviembre 2026, todavía no ocurrió' })).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getByRole('button', { name: 'Año siguiente, todavía no llegó' })).toHaveAttribute('aria-disabled', 'true');
  });

  it('shows the reason in a tooltip on a disabled month', async () => {
    setup();
    await open();

    await userEvent.hover(cell('noviembre 2026, todavía no ocurrió'));

    expect(await screen.findByRole('tooltip')).toHaveTextContent('Todavía no ocurrió');
  });

  it('opens a page of twelve years, pages back and picks a year', async () => {
    setup({ minMonth: '2010-01' });
    await open();

    await userEvent.click(screen.getByRole('button', { name: 'Elegir año, 2026' }));
    expect(screen.getByText('2015 – 2026')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '2026' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'Años siguientes, todavía no llegó' })).toHaveAttribute('aria-disabled', 'true');

    await userEvent.click(screen.getByRole('button', { name: 'Años anteriores' }));
    expect(screen.getByText('2003 – 2014')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '2005, sin datos de este año' })).toHaveAttribute('aria-disabled', 'true');

    await userEvent.click(screen.getByRole('button', { name: '2012' }));
    expect(screen.getByRole('group', { name: 'Meses de 2012' })).toBeInTheDocument();
  });

  it('does not page the years before the first year with data', async () => {
    setup();
    await open();
    await userEvent.click(screen.getByRole('button', { name: 'Elegir año, 2026' }));

    expect(screen.getByRole('button', { name: 'Años anteriores, sin datos de este año' })).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getByRole('button', { name: '2024, sin datos de este año' })).toHaveAttribute('aria-disabled', 'true');
  });

  it('applies the shortcuts to the current month and to the month before the current one', async () => {
    const onChange = setup({ value: '2026-05' });
    await open();
    await userEvent.click(screen.getByRole('button', { name: 'Mes anterior, septiembre 2026' }));
    expect(onChange).toHaveBeenLastCalledWith('2026-09');

    await open();
    await userEvent.click(screen.getByRole('button', { name: 'Este mes, octubre 2026' }));
    expect(onChange).toHaveBeenLastCalledWith('2026-10');
  });

  it('names the previous month on the shortcut and disables it when it has no data', async () => {
    const onChange = setup({ minMonth: '2026-10' });
    await open();

    const previous = screen.getByRole('button', { name: 'Mes anterior, septiembre 2026, sin datos de este mes' });
    expect(previous).toHaveTextContent('Mes anterior · sep');
    expect(previous).toHaveAttribute('aria-disabled', 'true');
    await userEvent.click(previous);
    expect(onChange).not.toHaveBeenCalled();
  });

  it('moves with the arrow keys and applies with Enter', async () => {
    const onChange = setup();
    await open();
    await waitFor(() => expect(cell('octubre 2026')).toHaveFocus());

    await userEvent.keyboard('{ArrowLeft}');
    expect(cell('septiembre 2026')).toHaveFocus();
    await userEvent.keyboard('{ArrowUp}');
    expect(cell('junio 2026')).toHaveFocus();
    await userEvent.keyboard('{ArrowRight}{ArrowDown}');
    expect(cell('octubre 2026')).toHaveFocus();
    await userEvent.keyboard('{ArrowRight}{ArrowDown}');
    expect(cell('octubre 2026')).toHaveFocus();
    await userEvent.keyboard('{ArrowLeft}{Enter}');

    expect(onChange).toHaveBeenCalledWith('2026-09');
  });

  it('changes the year with PageUp and PageDown keeping the month and stops at the limits', async () => {
    setup();
    await open();
    await waitFor(() => expect(cell('octubre 2026')).toHaveFocus());

    await userEvent.keyboard('{PageUp}');
    expect(screen.getByRole('group', { name: 'Meses de 2025' })).toBeInTheDocument();
    expect(cell('octubre 2025')).toHaveFocus();
    await userEvent.keyboard('{PageUp}');
    expect(screen.getByRole('group', { name: 'Meses de 2025' })).toBeInTheDocument();
    await userEvent.keyboard('{PageDown}');
    expect(screen.getByRole('group', { name: 'Meses de 2026' })).toBeInTheDocument();
  });

  it('crosses the year with the arrow keys', async () => {
    setup({ value: '2026-01' });
    await open();
    await waitFor(() => expect(cell('enero 2026')).toHaveFocus());

    await userEvent.keyboard('{ArrowLeft}');

    expect(screen.getByRole('group', { name: 'Meses de 2025' })).toBeInTheDocument();
    expect(cell('diciembre 2025')).toHaveFocus();
  });

  it('moves and picks years with the keyboard in the year view', async () => {
    setup({ minMonth: '2010-01' });
    await open();
    await userEvent.click(screen.getByRole('button', { name: 'Elegir año, 2026' }));
    await waitFor(() => expect(screen.getByRole('button', { name: '2026' })).toHaveFocus());

    await userEvent.keyboard('{ArrowLeft}{ArrowUp}');
    expect(screen.getByRole('button', { name: '2022' })).toHaveFocus();
    await userEvent.keyboard('{Enter}');

    expect(screen.getByRole('group', { name: 'Meses de 2022' })).toBeInTheDocument();
  });

  it('closes with Escape without applying', async () => {
    const onChange = setup();
    await open();

    await userEvent.keyboard('{Escape}');

    await waitFor(() => expect(screen.queryByRole('group', { name: 'Meses de 2026' })).not.toBeInTheDocument());
    expect(onChange).not.toHaveBeenCalled();
  });

  it('closes when the focus leaves the popover with Tab', async () => {
    setup();
    await open();
    await waitFor(() => expect(cell('octubre 2026')).toHaveFocus());

    await userEvent.tab();
    await userEvent.tab();
    await userEvent.tab();
    await userEvent.tab();

    await waitFor(() => expect(screen.queryByRole('group', { name: 'Meses de 2026' })).not.toBeInTheDocument());
  });

  it('is disabled with aria-disabled and a tooltip when there are no months with data', async () => {
    const onChange = setup({ disabled: true });

    expect(trigger()).toHaveAttribute('aria-disabled', 'true');
    await userEvent.click(trigger());
    expect(screen.queryByRole('group', { name: 'Meses de 2026' })).not.toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();

    await userEvent.hover(trigger());
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Todavía no hay meses con datos');
  });
});
