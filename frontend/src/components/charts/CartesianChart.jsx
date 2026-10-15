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
import { detectCurrency, formatAxisValue, formatChartValue, normalizeChartData } from './chartFormat';
import { AXIS_COLOR, GOAL_COLOR, GRID_COLOR, seriesColor, useChartAnimation } from './chartTheme';

const AXIS_TICK = { fill: AXIS_COLOR, fontSize: 12 };
const AXIS_WIDTH = 64;
const LEFT_AXIS = 'left';
const RIGHT_AXIS = 'right';

export function GoalLine({ value, label, axis = LEFT_AXIS }) {
  if (value == null) return null;
  return (
    <ReferenceLine
      yAxisId={axis}
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
  const yAxisId = item.axis === RIGHT_AXIS ? RIGHT_AXIS : LEFT_AXIS;
  const common = { dataKey: item.key, name: item.label, yAxisId, ...animation };
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
  const stackId = item.stack ?? (variant === 'stackedBar' ? 'stack' : undefined);
  return <Bar key={item.key} {...common} fill={color} stackId={stackId} radius={stackId ? 0 : [6, 6, 0, 0]} maxBarSize={48} />;
}

export function CartesianChart({
  variant,
  title,
  subtitle,
  info,
  badge,
  ariaLabel,
  data,
  series,
  tableSeries,
  unit,
  currency,
  goal,
  loading,
  error,
  onRetry,
  unavailableText,
  height,
  categoryLabel,
  emptyTitle,
  emptyDescription,
  className,
  onCategoryClick,
  openLabel,
}) {
  const animation = useChartAnimation();
  const Chart = CHART_BY_VARIANT[variant];
  const showLegend = series.length > 1;
  const unitBySeries = Object.fromEntries(series.filter((item) => item.unit).map((item) => [item.key, item.unit]));
  const rows = normalizeChartData(data, series);
  const resolvedCurrency = currency ?? detectCurrency(data, series) ?? 'ARS';
  const rightSeries = series.filter((item) => item.axis === RIGHT_AXIS);
  const leftUnit = unit ?? series.find((item) => item.axis !== RIGHT_AXIS)?.unit;
  const rightUnit = rightSeries[0]?.unit;
  const openRow = onCategoryClick ? (row) => onCategoryClick(row) : undefined;

  return (
    <ChartFrame
      title={title}
      subtitle={subtitle}
      info={info}
      badge={badge}
      ariaLabel={ariaLabel}
      data={data}
      series={series}
      tableSeries={tableSeries}
      unit={leftUnit}
      currency={resolvedCurrency}
      categoryLabel={categoryLabel}
      loading={loading}
      error={error}
      onRetry={onRetry}
      unavailableText={unavailableText}
      height={height}
      emptyTitle={emptyTitle}
      emptyDescription={emptyDescription}
      className={className}
      onRowOpen={openRow}
      openLabel={openLabel}
    >
      <ResponsiveContainer width="100%" height="100%">
        <Chart
          data={rows}
          margin={{ top: 8, right: 8, bottom: 0, left: 0 }}
          accessibilityLayer={false}
          onClick={
            openRow
              ? (state) => {
                  const row = rows.find((candidate) => candidate.label === state?.activeLabel);
                  if (row) openRow(row);
                }
              : undefined
          }
        >
          <CartesianGrid stroke={GRID_COLOR} strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="label" tick={AXIS_TICK} tickLine={false} axisLine={{ stroke: GRID_COLOR }} />
          <YAxis
            yAxisId={LEFT_AXIS}
            tick={AXIS_TICK}
            tickLine={false}
            axisLine={false}
            width={AXIS_WIDTH}
            tickFormatter={(value) => formatAxisValue(value, { unit: leftUnit, currency: resolvedCurrency })}
          />
          {rightSeries.length > 0 && (
            <YAxis
              yAxisId={RIGHT_AXIS}
              orientation="right"
              tick={AXIS_TICK}
              tickLine={false}
              axisLine={false}
              width={AXIS_WIDTH}
              tickFormatter={(value) => formatAxisValue(value, { unit: rightUnit, currency: resolvedCurrency })}
            />
          )}
          <Tooltip
            cursor={{ fill: 'var(--accent-soft)', opacity: 0.5 }}
            content={<ChartTooltip unit={leftUnit} currency={resolvedCurrency} unitBySeries={unitBySeries} />}
          />
          {showLegend && <Legend iconType="circle" wrapperStyle={{ fontSize: 12, color: AXIS_COLOR }} />}
          {series.map((item, index) => renderSeries(variant, item, index, animation))}
          {goal && <GoalLine value={goal.value} label={goal.label} axis={goal.axis} />}
        </Chart>
      </ResponsiveContainer>
    </ChartFrame>
  );
}
