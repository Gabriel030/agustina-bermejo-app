import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { ChartFrame } from './ChartFrame';
import { formatAxisValue, formatChartValue } from './chartFormat';
import { AXIS_COLOR, GOAL_COLOR, GRID_COLOR, seriesColor, useChartAnimation } from './chartTheme';

const AXIS_TICK = { fill: AXIS_COLOR, fontSize: 12 };

export function GoalLine({ value, label }) {
  if (value == null) return null;
  return (
    <ReferenceLine
      y={value}
      stroke={GOAL_COLOR}
      strokeDasharray="6 4"
      ifOverflow="extendDomain"
      label={{ value: label, position: 'insideTopRight', fill: GOAL_COLOR, fontSize: 12 }}
    />
  );
}

function ChartTooltip({ active, payload, label, unit, currency, unitBySeries }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-xl border border-border bg-surface px-3 py-2 text-xs shadow-lg backdrop-blur-[28px]">
      <div className="mb-1 font-semibold text-ink">{label}</div>
      <div className="flex flex-col gap-0.5">
        {payload.map((entry) => (
          <div key={entry.dataKey} className="flex items-center justify-between gap-4 text-ink-soft">
            <span className="flex items-center gap-1.5">
              <span className="size-2 rounded-full" style={{ background: entry.color }} />
              {entry.name}
            </span>
            <span className="font-medium tabular-nums text-ink">
              {formatChartValue(entry.value, { unit: unitBySeries[entry.dataKey] ?? unit, currency })}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

const CHART_BY_VARIANT = {
  bar: BarChart,
  stackedBar: BarChart,
  line: LineChart,
  area: AreaChart,
  composed: ComposedChart,
};

function renderSeries(variant, item, index, animation) {
  const color = seriesColor(item, index);
  const common = { dataKey: item.key, name: item.label, ...animation };
  const kind = variant === 'composed' ? item.type ?? 'bar' : variant;
  if (kind === 'line') {
    return (
      <Line
        key={item.key}
        {...common}
        type="monotone"
        stroke={color}
        strokeWidth={2.5}
        dot={{ r: 3, fill: color, strokeWidth: 0 }}
        activeDot={{ r: 5 }}
        connectNulls={false}
      />
    );
  }
  if (kind === 'area') {
    return <Area key={item.key} {...common} type="monotone" stackId="stack" stroke={color} fill={color} fillOpacity={0.35} />;
  }
  return (
    <Bar
      key={item.key}
      {...common}
      fill={color}
      stackId={variant === 'stackedBar' ? 'stack' : undefined}
      radius={variant === 'stackedBar' ? 0 : [6, 6, 0, 0]}
      maxBarSize={48}
    />
  );
}

export function CartesianChart({
  variant,
  title,
  subtitle,
  ariaLabel,
  data,
  series,
  unit,
  currency,
  goal,
  loading,
  height,
  categoryLabel,
  emptyTitle,
  emptyDescription,
  className,
  onCategoryClick,
}) {
  const animation = useChartAnimation();
  const Chart = CHART_BY_VARIANT[variant];
  const showLegend = series.length > 1;
  const unitBySeries = Object.fromEntries(series.filter((item) => item.unit).map((item) => [item.key, item.unit]));

  return (
    <ChartFrame
      title={title}
      subtitle={subtitle}
      ariaLabel={ariaLabel}
      data={data}
      series={series}
      unit={unit}
      currency={currency}
      categoryLabel={categoryLabel}
      loading={loading}
      height={height}
      emptyTitle={emptyTitle}
      emptyDescription={emptyDescription}
      className={className}
    >
      <ResponsiveContainer width="100%" height="100%">
        <Chart
          data={data}
          margin={{ top: 8, right: 8, bottom: 0, left: 0 }}
          accessibilityLayer={false}
          onClick={onCategoryClick ? (state) => state?.activeLabel != null && onCategoryClick(state.activeLabel) : undefined}
        >
          <CartesianGrid stroke={GRID_COLOR} strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="label" tick={AXIS_TICK} tickLine={false} axisLine={{ stroke: GRID_COLOR }} />
          <YAxis
            tick={AXIS_TICK}
            tickLine={false}
            axisLine={false}
            width={64}
            tickFormatter={(value) => formatAxisValue(value, { unit, currency })}
          />
          <Tooltip
            cursor={{ fill: 'var(--accent-soft)', opacity: 0.5 }}
            content={<ChartTooltip unit={unit} currency={currency} unitBySeries={unitBySeries} />}
          />
          {showLegend && <Legend iconType="circle" wrapperStyle={{ fontSize: 12, color: AXIS_COLOR }} />}
          {series.map((item, index) => renderSeries(variant, item, index, animation))}
          {goal && <GoalLine value={goal.value} label={goal.label} />}
        </Chart>
      </ResponsiveContainer>
    </ChartFrame>
  );
}
