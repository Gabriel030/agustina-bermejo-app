import { useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Check, X } from 'lucide-react';
import { ComposedTrendChart } from '@/components/charts';
import { StatusPill } from '@/components/StatusPill';
import { cn } from '@/lib/utils';
import { useMonthlyMetric } from '@/features/billing/hooks/useMonthlyMetric';
import { formatShortMonth, goalStatusAt } from './dashboardMetrics';

const EMPTY = [];

function GoalBadge({ status, month }) {
  if (!status) return null;
  const met = status === 'MET';
  return (
    <StatusPill tone={met ? 'success' : 'warning'} className={cn('gap-1 px-2.5', met ? 'text-success-ink' : 'text-warning-ink')}>
      {met ? <Check className="size-3" /> : <X className="size-3" />}
      <span>
        {met ? 'Cumplida' : 'Por debajo de la meta'} · {formatShortMonth(month)}
      </span>
    </StatusPill>
  );
}

function PartialBadge() {
  return (
    <StatusPill tone="warning" className="px-2.5 text-warning-ink">
      Último dato del mes
    </StatusPill>
  );
}

function withData(built) {
  if (!built) return null;
  const hasValue = (item) => built.rows.some((row) => row[item.key] != null);
  const series = built.series.filter(hasValue);
  if (series.length === 0) return { ...built, rows: [], series: [], tableSeries: undefined };
  return { ...built, series, tableSeries: built.tableSeries?.filter(hasValue) };
}

export function MetricPanel({ panel, filters, month, className }) {
  const query = useMonthlyMetric(panel.id, filters);
  const metric = query.data;
  const unavailable = metric && !metric.available ? panel.unavailableText : undefined;
  const built = useMemo(() => (metric?.available ? withData(panel.build(metric)) : null), [panel, metric]);
  const navigate = useNavigate();
  const rolePrefix = useLocation().pathname.split('/')[1];
  const drilldown = panel.drilldown;
  const months = metric?.months ?? [];
  const period = months.length ? `${formatShortMonth(months[0])} a ${formatShortMonth(months[months.length - 1])}` : '';

  return (
    <ComposedTrendChart
      title={panel.title}
      subtitle={panel.subtitle}
      info={panel.info}
      ariaLabel={`${panel.title}, ${period}`}
      badge={
        <>
          {built?.goal && <GoalBadge status={goalStatusAt(metric, month)} month={month} />}
          {metric?.partial && <PartialBadge />}
        </>
      }
      onCategoryClick={drilldown ? () => navigate(`/${rolePrefix}/${drilldown.path}`) : undefined}
      openLabel={drilldown?.label}
      data={built?.rows ?? EMPTY}
      series={built?.series ?? EMPTY}
      tableSeries={built?.tableSeries}
      goal={built?.goal}
      loading={query.isLoading}
      error={query.isError}
      onRetry={() => query.refetch()}
      unavailableText={unavailable}
      height={260}
      className={className}
    />
  );
}
