import { useState } from 'react';
import { BarChart3, Table2 } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { ChartDataTable } from './ChartDataTable';

export function ChartFrame({
  title,
  subtitle,
  ariaLabel,
  data,
  series,
  unit,
  currency,
  categoryLabel,
  loading = false,
  height = 280,
  emptyTitle = 'Sin datos para mostrar',
  emptyDescription,
  className,
  children,
}) {
  const [showTable, setShowTable] = useState(false);
  const isEmpty = !loading && (!data || data.length === 0);
  const hasContent = !loading && !isEmpty;

  return (
    <GlassCard className={`flex min-w-0 flex-col gap-3 p-5 ${className ?? ''}`}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="font-heading text-[15px] font-semibold text-ink">{title}</h3>
          {subtitle && <p className="text-xs text-ink-faint">{subtitle}</p>}
        </div>
        {hasContent && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            aria-pressed={showTable}
            onClick={() => setShowTable((current) => !current)}
          >
            {showTable ? <BarChart3 className="size-4" /> : <Table2 className="size-4" />}
            {showTable ? 'Ver como gráfico' : 'Ver como tabla'}
          </Button>
        )}
      </div>

      {loading && (
        <div data-testid="chart-skeleton" style={{ height }} className="w-full">
          <Skeleton className="h-full w-full" />
        </div>
      )}
      {isEmpty && <EmptyState title={emptyTitle} description={emptyDescription} />}
      {hasContent && showTable && (
        <ChartDataTable data={data} series={series} unit={unit} currency={currency} categoryLabel={categoryLabel} />
      )}
      {hasContent && !showTable && (
        <div role="img" aria-label={ariaLabel ?? title} style={{ height }} className="w-full min-w-0">
          {children}
        </div>
      )}
    </GlassCard>
  );
}
