import { cloneElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ComposedTrendChart } from './ComposedTrendChart';
import { detectCurrency, formatChartValue, normalizeChartData, numericValue } from './chartFormat';

vi.mock('recharts', async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...original,
    ResponsiveContainer: ({ children }) => cloneElement(children, { width: 640, height: 260 }),
  };
});

const originalMatchMedia = window.matchMedia;

beforeEach(() => {
  window.matchMedia = () => ({ matches: true, addEventListener() {}, removeEventListener() {} });
});

afterEach(() => {
  window.matchMedia = originalMatchMedia;
});

const EXPECTED_LEFT = ['$0', '$50k', '$100k', '$150k', '$200k'];
const EXPECTED_RIGHT = ['0%', '3%', '6%', '9%', '12%'];
const normalize = (text) => text.replace(/\s/g, ' ');
const money = (amount) => ({ amount, currency: 'ARS' });

const data = [
  { label: 'feb 26', invoices: 38, amount: money('380000.00'), onTime: 80 },
  { label: 'mar 26', invoices: 40, amount: money('1234.56'), onTime: null },
];
const series = [
  { key: 'invoices', label: 'Facturas', unit: 'count' },
  { key: 'amount', label: 'Monto emitido', unit: 'money', type: 'line', axis: 'right' },
];

function yAxes(container) {
  return [...container.querySelectorAll('.recharts-yAxis-tick-labels')].map((axis) =>
    [...axis.querySelectorAll('text')].map((node) => node.textContent.replace(/\s/g, '').toLowerCase()),
  );
}

describe('chart data contract (Money or numbers)', () => {
  it('reads the amount of a Money and the number itself', () => {
    expect(numericValue(money('1234.56'))).toBe(1234.56);
    expect(numericValue(12)).toBe(12);
    expect(numericValue(null)).toBeNull();
  });

  it('formats a Money with its own currency and ignores the unit hint', () => {
    expect(normalize(formatChartValue({ amount: '10.00', currency: 'USD' }, { unit: 'count' }))).toMatch(/US\$\s?10,00/);
  });

  it('normalizes Money cells of the chart series and keeps the originals untouched', () => {
    const rows = normalizeChartData(data, series);
    expect(rows[0].amount).toBe(380000);
    expect(data[0].amount).toEqual(money('380000.00'));
    expect(detectCurrency(data, series)).toBe('ARS');
  });
});

describe('ComposedTrendChart real render', () => {
  it('draws bars and a line, with compact money on the secondary axis and counts on the primary one', () => {
    const { container } = render(<ComposedTrendChart title="Emisión" data={data} series={series} />);

    expect(container.querySelectorAll('.recharts-bar-rectangle').length).toBeGreaterThan(0);
    expect(container.querySelector('.recharts-line')).not.toBeNull();
    const [left, right] = yAxes(container);
    expect(yAxes(container)).toHaveLength(2);
    expect(left.length).toBeGreaterThan(0);
    expect(left.every((text) => /^\d+$/.test(text))).toBe(true);
    expect(right.some((text) => /\$/.test(text) && /mil|k|K/i.test(text))).toBe(true);
  });

  it('puts percentages on the secondary axis next to money on the primary one', () => {
    const mixed = [
      { label: 'feb 26', foregone: money('200000.00'), percent: 10 },
      { label: 'mar 26', foregone: money('100000.00'), percent: 5 },
    ];
    const { container } = render(
      <ComposedTrendChart
        title="Descuentos"
        data={mixed}
        series={[
          { key: 'foregone', label: 'Resignado', unit: 'money' },
          { key: 'percent', label: '% sobre el bruto', unit: 'percent', type: 'line', axis: 'right' },
        ]}
      />,
    );

    const [left, right] = yAxes(container);
    expect(left).toEqual(EXPECTED_LEFT);
    expect(right).toEqual(EXPECTED_RIGHT);
  });

  it('draws the goal line and extends the axis so a goal above the data stays visible', () => {
    const low = [
      { label: 'feb 26', onTime: 40 },
      { label: 'mar 26', onTime: 50 },
    ];
    const { container } = render(
      <ComposedTrendChart
        title="Pago a término"
        data={low}
        series={[{ key: 'onTime', label: 'A término', unit: 'percent', type: 'line' }]}
        goal={{ value: 85, label: 'Meta 85 %', axis: 'left' }}
      />,
    );

    expect(container.querySelector('.recharts-reference-line')).not.toBeNull();
    expect(container.textContent).toContain('Meta 85 %');
    const ticks = yAxes(container)[0].map((text) => Number.parseFloat(text));
    expect(Math.max(...ticks)).toBeGreaterThanOrEqual(85);
  });

  it('draws nothing for a goal that is not configured', () => {
    const { container } = render(
      <ComposedTrendChart title="Pago a término" data={data} series={[series[0]]} goal={undefined} />,
    );
    expect(container.querySelector('.recharts-reference-line')).toBeNull();
  });
});

describe('chart table and drill-down', () => {
  it('shows Money values formatted in the table', async () => {
    render(<ComposedTrendChart title="Emisión" data={data} series={series} />);

    await userEvent.click(screen.getByRole('button', { name: 'Ver como tabla' }));

    expect(normalize(screen.getByText(/1\.234,56/).textContent)).toBe('$ 1.234,56');
    expect(normalize(screen.getByText(/380\.000,00/).textContent)).toBe('$ 380.000,00');
  });

  it('opens a month from the table with the keyboard', async () => {
    const onCategoryClick = vi.fn();
    render(<ComposedTrendChart title="Emisión" data={data} series={series} onCategoryClick={onCategoryClick} openLabel="Abrir Corridas" />);

    await userEvent.click(screen.getByRole('button', { name: 'Ver como tabla' }));
    const open = screen.getByRole('button', { name: 'Abrir Corridas: mar 26' });
    open.focus();
    await userEvent.keyboard('{Enter}');

    expect(onCategoryClick).toHaveBeenCalledWith(expect.objectContaining({ label: 'mar 26' }));
  });

  it('offers no open button when nobody handles the drill-down', async () => {
    render(<ComposedTrendChart title="Emisión" data={data} series={series} />);
    await userEvent.click(screen.getByRole('button', { name: 'Ver como tabla' }));
    expect(within(screen.getByRole('table')).queryByRole('button')).not.toBeInTheDocument();
  });

  it('shows the long-title header with the table button reachable by name', () => {
    render(
      <ComposedTrendChart
        title="Comprobantes de transferencia subidos por resultado y revisados"
        subtitle="Subidos por resultado y días hábiles de revisión"
        data={data}
        series={series}
      />,
    );
    expect(screen.getByRole('button', { name: 'Ver como tabla' })).toBeInTheDocument();
  });

  it('renders the unavailable and error states with retry', async () => {
    const onRetry = vi.fn();
    const { rerender } = render(<ComposedTrendChart title="Recargos" data={[]} series={series} unavailableText="Disponible cuando esté la mora" />);
    expect(screen.getByText('Disponible cuando esté la mora')).toBeInTheDocument();

    rerender(<ComposedTrendChart title="Recargos" data={[]} series={series} error onRetry={onRetry} />);
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(onRetry).toHaveBeenCalled();
  });
});
