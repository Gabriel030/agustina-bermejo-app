import { cloneElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from '@testing-library/react';
import { ComposedTrendChart } from './ComposedTrendChart';
import { StackedBarChart } from './StackedBarChart';

const calls = vi.hoisted(() => []);

vi.mock('recharts', async (importOriginal) => {
  const original = await importOriginal();
  const spy = (name) => {
    const Original = original[name];
    return function Spy(props) {
      calls.push({ name, props });
      return <Original {...props} />;
    };
  };
  return {
    ...original,
    ComposedChart: spy('ComposedChart'),
    BarChart: spy('BarChart'),
    Bar: spy('Bar'),
    Line: spy('Line'),
    YAxis: spy('YAxis'),
    ReferenceLine: spy('ReferenceLine'),
    ResponsiveContainer: ({ children }) => cloneElement(children, { width: 640, height: 260 }),
  };
});

const originalMatchMedia = window.matchMedia;

function mockMotion(reduced) {
  window.matchMedia = () => ({ matches: reduced, addEventListener() {}, removeEventListener() {} });
}

beforeEach(() => {
  calls.length = 0;
});

afterEach(() => {
  window.matchMedia = originalMatchMedia;
});

const propsOf = (name) => calls.filter((call) => call.name === name).map((call) => call.props);
const lastPropsOf = (name) => propsOf(name).at(-1);

const data = [
  { label: 'feb 26', approved: 10, rejected: 2, days: 1.5, other: 4 },
  { label: 'mar 26', approved: 12, rejected: 1, days: 2.5, other: 5 },
];

describe('chart wrappers pass the right props to the library', () => {
  it('stacks only the series that declare a stack and keeps the others unstacked', () => {
    mockMotion(false);
    render(
      <ComposedTrendChart
        title="Comprobantes"
        data={data}
        series={[
          { key: 'approved', label: 'Aprobados', stack: 'receipts' },
          { key: 'rejected', label: 'Rechazados', stack: 'receipts' },
          { key: 'other', label: 'Otros' },
          { key: 'days', label: 'Días', unit: 'days', type: 'line', axis: 'right' },
        ]}
      />,
    );

    const stackOf = (key) => propsOf('Bar').find((props) => props.dataKey === key).stackId;
    expect(stackOf('approved')).toBe('receipts');
    expect(stackOf('rejected')).toBe('receipts');
    expect(stackOf('other')).toBeUndefined();
  });

  it('stacks every bar of the stacked wrapper under one stack', () => {
    mockMotion(false);
    render(<StackedBarChart title="Becas" data={data} series={[{ key: 'approved', label: 'A' }, { key: 'rejected', label: 'B' }]} />);

    expect(new Set(propsOf('Bar').map((props) => props.stackId))).toEqual(new Set(['stack']));
  });

  it('turns the animation off for every series when the user prefers reduced motion', () => {
    mockMotion(true);
    render(
      <ComposedTrendChart
        title="Comprobantes"
        data={data}
        series={[{ key: 'approved', label: 'A' }, { key: 'days', label: 'D', type: 'line', axis: 'right' }]}
      />,
    );

    expect(propsOf('Bar').length).toBeGreaterThan(0);
    expect(propsOf('Line').length).toBeGreaterThan(0);
    expect([...propsOf('Bar'), ...propsOf('Line')].every((props) => props.isAnimationActive === false)).toBe(true);
  });

  it('animates every series by default', () => {
    mockMotion(false);
    render(<ComposedTrendChart title="C" data={data} series={[{ key: 'approved', label: 'A' }]} />);

    expect(propsOf('Bar').every((props) => props.isAnimationActive === true)).toBe(true);
  });

  it('binds the right axis to the series that asks for it and the goal to the axis it is expressed in', () => {
    mockMotion(false);
    render(
      <ComposedTrendChart
        title="Comprobantes"
        data={data}
        series={[
          { key: 'approved', label: 'Aprobados', unit: 'count' },
          { key: 'days', label: 'Días', unit: 'days', type: 'line', axis: 'right' },
        ]}
        goal={{ value: 3, label: 'Meta 3 d', axis: 'right' }}
      />,
    );

    expect(lastPropsOf('Bar').yAxisId).toBe('left');
    expect(lastPropsOf('Line').yAxisId).toBe('right');
    expect(propsOf('YAxis').map((props) => props.yAxisId).sort()).toEqual(['left', 'right']);
    expect(propsOf('YAxis').find((props) => props.yAxisId === 'right').orientation).toBe('right');
    expect(propsOf('YAxis').find((props) => props.yAxisId === 'left').width).toBe(64);
    expect(lastPropsOf('ReferenceLine')).toMatchObject({ y: 3, yAxisId: 'right', ifOverflow: 'extendDomain' });
  });

  it('moves the right series to the primary axis when no series uses the left one', () => {
    mockMotion(false);
    render(
      <ComposedTrendChart
        title="Descuentos"
        data={data}
        series={[{ key: 'days', label: 'Días', unit: 'days', type: 'line', axis: 'right' }]}
        goal={{ value: 3, label: 'Meta', axis: 'right' }}
      />,
    );

    expect(propsOf('YAxis').map((props) => props.yAxisId)).toEqual(['left']);
    expect(lastPropsOf('Line').yAxisId).toBe('left');
    expect(lastPropsOf('ReferenceLine').yAxisId).toBe('left');
  });

  it('opens the row of the clicked category and ignores clicks outside any category', () => {
    mockMotion(false);
    const onCategoryClick = vi.fn();
    render(<ComposedTrendChart title="C" data={data} series={[{ key: 'approved', label: 'A' }]} onCategoryClick={onCategoryClick} />);

    const { onClick } = lastPropsOf('ComposedChart');
    onClick({ activeLabel: 'mar 26' });
    expect(onCategoryClick).toHaveBeenCalledTimes(1);
    expect(onCategoryClick).toHaveBeenCalledWith(expect.objectContaining({ label: 'mar 26', approved: 12 }));

    onClick({ activeLabel: undefined });
    onClick(undefined);
    expect(onCategoryClick).toHaveBeenCalledTimes(1);
  });

  it('registers no click handler when nobody handles the drill-down', () => {
    mockMotion(false);
    render(<ComposedTrendChart title="C" data={data} series={[{ key: 'approved', label: 'A' }]} />);

    expect(lastPropsOf('ComposedChart').onClick).toBeUndefined();
  });
});
