import { useState } from 'react';
import { BarChart3, Table2 } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { ChartDataTable } from './ChartDataTable';
import { InfoTip } from './InfoTip';

export function ChartFrame({
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
  categoryLabel,
  loading = false,
  error = false,
  onRetry,
  unavailableText,
  height = 280,
  emptyTitle = 'Sin datos para mostrar',
  emptyDescription,
  className,
  onRowOpen,
  openLabel,
  children,
}) {
  const [showTable, setShowTable] = useState(false);
  const unavailable = !loading && !error && Boolean(unavailableText);
  const isEmpty = !loading && !error && !unavailable && (!data || data.length === 0);
  const hasContent = !loading && !error && !unavailable && !isEmpty;
  const toggleLabel = showTable ? 'Ver como gráfico' : 'Ver como tabla';

  return (
    <GlassCard className={`flex min-w-0 flex-col gap-3 p-5 ${className ?? ''}`}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-1.5">
            <h3 className="min-w-0 font-heading text-[15px] font-semibold text-ink">{title}</h3>
            {info && <InfoTip label={`Qué muestra ${title}`}>{info}</InfoTip>}
          </div>
          {subtitle && <p className="text-xs text-ink-soft">{subtitle}</p>}
        </div>
        <div className="flex shrink-0 items-center gap-2">
          {hasContent && badge}
          {hasContent && (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              aria-pressed={showTable}
              aria-label={toggleLabel}
              onClick={() => setShowTable((current) => !current)}
            >
              {showTable ? <BarChart3 className="size-4" /> : <Table2 className="size-4" />}
              <span className="hidden sm:inline">{toggleLabel}</span>
            </Button>
          )}
        </div>
      </div>

      {loading && (
        <div data-testid="chart-skeleton" style={{ height }} className="w-full">
          <Skeleton className="h-full w-full" />
        </div>
      )}
      {error && (
        <EmptyState
          title="No se pudo cargar"
          action={
            onRetry && (
              <Button type="button" variant="outline" size="sm" onClick={onRetry}>
                Reintentar
              </Button>
            )
          }
        />
      )}
      {unavailable && <EmptyState title={unavailableText} />}
      {isEmpty && <EmptyState title={emptyTitle} description={emptyDescription} />}
      {hasContent && showTable && (
        <ChartDataTable
          data={data}
          series={tableSeries ?? series}
          unit={unit}
          currency={currency}
          categoryLabel={categoryLabel}
          onRowOpen={onRowOpen}
          openLabel={openLabel}
        />
      )}
      {hasContent && !showTable && (
        <div role="img" aria-label={ariaLabel ?? title} style={{ height }} className="w-full min-w-0">
          {children}
        </div>
      )}
    </GlassCard>
  );
}
