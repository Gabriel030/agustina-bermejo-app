import { afterEach, describe, expect, it } from 'vitest';
import { render, renderHook, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import packageJson from '../../../package.json?raw';
import tokensCss from '@/theme/tokens.css?raw';
import { ProgressBar } from '@/components/ProgressBar';
import { ChartFrame } from './ChartFrame';
import { TrendBarChart } from './TrendBarChart';
import { StackedBarChart } from './StackedBarChart';
import { TrendLineChart } from './TrendLineChart';
import { StackedAreaChart } from './StackedAreaChart';
import { ComposedTrendChart } from './ComposedTrendChart';
import { formatChartValue } from './chartFormat';
import { SERIES_COLORS, useChartAnimation } from './chartTheme';

const normalize = (text) => text.replace(/\s/g, ' ');

const data = [
  { label: 'Feb', paid: 1234.56, due: 10 },
  { label: 'Mar', paid: 80, due: null },
];
const series = [
  { key: 'paid', label: 'Pagado' },
  { key: 'due', label: 'Vencido' },
];

const originalMatchMedia = window.matchMedia;

afterEach(() => {
  window.matchMedia = originalMatchMedia;
});

function mockReducedMotion(matches) {
  window.matchMedia = () => ({ matches, addEventListener() {}, removeEventListener() {} });
}

describe('ChartFrame', () => {
  it('shows a real table with formatted values when the user asks for it', async () => {
    render(
      <ChartFrame title="Cobrado por mes" data={data} series={series} unit="money">
        <div />
      </ChartFrame>,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Ver como tabla' }));

    const table = screen.getByRole('table');
    expect(within(table).getByRole('columnheader', { name: 'Pagado' })).toBeInTheDocument();
    expect(normalize(within(table).getByText(/1\.234,56/).textContent)).toBe('$ 1.234,56');
    expect(within(table).getByText('—')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ver como gráfico' })).toBeInTheDocument();
  });

  it('formats percentages and empty values', () => {
    expect(normalize(formatChartValue(45, { unit: 'percent' }))).toBe('45 %');
    expect(formatChartValue(null, { unit: 'percent' })).toBe('—');
  });

  it('renders a skeleton while loading and an empty state without data', () => {
    const { rerender } = render(<ChartFrame title="Cobrado" loading data={[]} series={series} />);
    expect(screen.getByTestId('chart-skeleton')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ver como tabla' })).not.toBeInTheDocument();

    rerender(<ChartFrame title="Cobrado" data={[]} series={series} />);
    expect(screen.queryByTestId('chart-skeleton')).not.toBeInTheDocument();
    expect(screen.getByText('Sin datos para mostrar')).toBeInTheDocument();
  });
});

describe('chart wrappers', () => {
  const wrappers = {
    TrendBarChart,
    StackedBarChart,
    TrendLineChart,
    StackedAreaChart,
    ComposedTrendChart,
  };

  it.each(Object.entries(wrappers))('%s exposes an image role with a descriptive label', (_name, Wrapper) => {
    render(
      <Wrapper
        title="Cobrado por mes"
        ariaLabel="Gráfico de barras del cobrado por mes, de febrero a marzo"
        data={data}
        series={series}
        unit="money"
      />,
    );

    expect(
      screen.getByRole('img', { name: 'Gráfico de barras del cobrado por mes, de febrero a marzo' }),
    ).toBeInTheDocument();
  });

  it('falls back to the title as accessible name', () => {
    render(<TrendBarChart title="Cobrado por mes" data={data} series={series} unit="money" />);
    expect(screen.getByRole('img', { name: 'Cobrado por mes' })).toBeInTheDocument();
  });
});

describe('useChartAnimation', () => {
  it('disables animation when the user prefers reduced motion', () => {
    mockReducedMotion(true);
    const { result } = renderHook(() => useChartAnimation());
    expect(result.current.isAnimationActive).toBe(false);
  });

  it('animates by default', () => {
    mockReducedMotion(false);
    const { result } = renderHook(() => useChartAnimation());
    expect(result.current.isAnimationActive).toBe(true);
  });
});

describe('ProgressBar', () => {
  it('exposes progressbar semantics with the formatted text', () => {
    render(<ProgressBar value={45} label="Avance" />);

    const bar = screen.getByRole('progressbar', { name: 'Avance' });
    expect(bar).toHaveAttribute('aria-valuenow', '45');
    expect(normalize(screen.getByText(/45/).textContent)).toBe('45 %');
  });

  it('clamps out-of-range values', () => {
    render(<ProgressBar value={140} label="Avance" />);
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100');
  });
});

describe('library and theme guardrails', () => {
  it('pins recharts to an exact version', () => {
    expect(JSON.parse(packageJson).dependencies.recharts).toBe('3.8.0');
  });

  it('draws series only from design tokens defined for light and dark', () => {
    const extraTokens = ['--border', '--ink-faint', '--ink-soft', '--accent-soft', '--warning-ink', '--success-ink'];
    const seriesTokens = SERIES_COLORS.map((color) => {
      const token = color.match(/^var\((--[\w-]+)\)$/)?.[1];
      expect(token, color).toBeTruthy();
      return token;
    });
    for (const token of [...seriesTokens, ...extraTokens]) {
      expect(tokensCss.split(`${token}:`).length - 1, token).toBeGreaterThanOrEqual(3);
    }
  });
});
