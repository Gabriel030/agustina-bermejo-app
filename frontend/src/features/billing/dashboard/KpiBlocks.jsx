import { ArrowDownRight, ArrowUpRight, Check, Minus, X } from 'lucide-react';
import { StatCard } from '@/components/StatCard';
import { Stagger } from '@/components/Stagger';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { formatChartValue } from '@/components/charts';
import { useFinancialDashboard } from '@/features/billing/hooks/useFinancialDashboard';
import { useMonthlyMetric } from '@/features/billing/hooks/useMonthlyMetric';
import { cn, formatMoney } from '@/lib/utils';
import { deltaBetween, formatLongMonth, formatPointsDelta, formatShortMonth, goalStatusAt, previousMonthOf, valueAt } from './dashboardMetrics';
import { issuedInvoiceCount } from './financialPanels';

const GRID = 'grid gap-4 [grid-template-columns:repeat(auto-fit,minmax(14rem,1fr))]';

function DeltaLine({ delta, text, betterWhen, previousMonth }) {
  if (delta === null) return <span className="text-xs text-ink-soft">Sin dato previo</span>;
  const reference = `vs ${formatShortMonth(previousMonth)}`;
  if (delta === 0) {
    return (
      <span className="flex items-center gap-1 text-xs font-semibold text-ink-soft">
        <Minus className="size-3.5" />
        Igual {reference}
      </span>
    );
  }
  const up = delta > 0;
  const good = betterWhen === 'UP' ? up : !up;
  const Icon = up ? ArrowUpRight : ArrowDownRight;
  return (
    <span className={cn('flex items-center gap-0.5 text-xs font-bold tabular-nums', good ? 'text-success-ink' : 'text-warning-ink')}>
      <Icon className="size-3.5" />
      {text}
      <span className="ml-1 font-medium text-ink-soft">{reference}</span>
    </span>
  );
}

function GoalChip({ status }) {
  if (!status) return null;
  const met = status === 'MET';
  return (
    <StatusPill tone={met ? 'success' : 'warning'} className={cn('gap-1 px-2.5', met ? 'text-success-ink' : 'text-warning-ink')}>
      {met ? <Check className="size-3" /> : <X className="size-3" />}
      {met ? 'Cumplida' : 'Por debajo de la meta'}
    </StatusPill>
  );
}

function Value({ loading, error, onRetry, children, tone = 'text-ink' }) {
  if (error) {
    return (
      <div className="flex flex-col items-start gap-1">
        <span className="text-sm text-ink-soft">No se pudo cargar</span>
        <Button type="button" variant="outline" size="sm" onClick={onRetry}>
          Reintentar
        </Button>
      </div>
    );
  }
  return <div className={cn('font-heading text-3xl font-bold', tone)}>{loading ? '…' : children}</div>;
}

function countDelta(current, previous, singular, plural) {
  const delta = deltaBetween(current, previous);
  if (delta === null) return { delta: null };
  const amount = Math.abs(delta);
  return { delta, text: `${delta > 0 ? '+' : '−'}${formatChartValue(amount)} ${amount === 1 ? singular : plural}` };
}

function Footer({ children }) {
  return <div className="mt-2 flex min-h-6 flex-col items-start gap-1.5">{children}</div>;
}

export function MonthKpis({ filters, month }) {
  const onTime = useMonthlyMetric('on-time-payment', filters);
  const issued = useMonthlyMetric('issued-invoices', filters);
  const lateFees = useMonthlyMetric('late-fees', filters);

  const previousOnTime = previousMonthOf(onTime.data, month);
  const onTimeNow = valueAt(onTime.data, 'onTimePercent', month);
  const onTimeDelta = deltaBetween(onTimeNow, valueAt(onTime.data, 'onTimePercent', previousOnTime));

  const previousIssued = previousMonthOf(issued.data, month);
  const issuedDelta = countDelta(issuedInvoiceCount(issued.data, month), issuedInvoiceCount(issued.data, previousIssued), 'factura', 'facturas');

  const previousFees = previousMonthOf(lateFees.data, month);
  const feesCount = valueAt(lateFees.data, 'issuedCount', month);
  const feesDelta = countDelta(feesCount, valueAt(lateFees.data, 'issuedCount', previousFees), 'nota', 'notas');
  const feesAvailable = lateFees.data?.available !== false;

  return (
    <section aria-label={formatLongMonth(month)} className="flex min-w-0 flex-col gap-2">
      <h2 className="text-[11px] font-bold uppercase tracking-wide text-ink-soft">{formatLongMonth(month)}</h2>
      <div className={GRID}>
        <Stagger className="min-w-0">
          <StatCard label="Pago a término">
            <Value loading={onTime.isLoading} error={onTime.isError} onRetry={() => onTime.refetch()}>
              {formatChartValue(onTimeNow, { unit: 'percent' })}
            </Value>
            {!onTime.isError && (
              <Footer>
                <GoalChip status={goalStatusAt(onTime.data, month)} />
                <DeltaLine delta={onTimeDelta} text={formatPointsDelta(onTimeDelta)} betterWhen="UP" previousMonth={previousOnTime} />
              </Footer>
            )}
          </StatCard>
          <StatCard label="Facturas emitidas">
            <Value loading={issued.isLoading} error={issued.isError} onRetry={() => issued.refetch()}>
              {formatChartValue(issuedInvoiceCount(issued.data, month))}
            </Value>
            {!issued.isError && (
              <Footer>
                <DeltaLine {...issuedDelta} betterWhen="UP" previousMonth={previousIssued} />
              </Footer>
            )}
          </StatCard>
          <StatCard label="Recargos">
            <Value loading={lateFees.isLoading} error={lateFees.isError} onRetry={() => lateFees.refetch()}>
              {feesAvailable ? formatMoney(valueAt(lateFees.data, 'issuedAmount', month)) || '—' : '—'}
            </Value>
            {!lateFees.isError && (
              <Footer>
                <span className="text-xs tabular-nums text-ink-soft">
                  {feesAvailable ? `${formatChartValue(feesCount)} notas de débito` : 'Disponible cuando esté la mora'}
                </span>
                <DeltaLine {...feesDelta} betterWhen="DOWN" previousMonth={previousFees} />
              </Footer>
            )}
          </StatCard>
        </Stagger>
      </div>
    </section>
  );
}

export function TodayKpis({ currency }) {
  const dashboard = useFinancialDashboard();
  const { data, isLoading, isError } = dashboard;
  const retry = () => dashboard.refetch();
  const totals = (data?.totalsByCurrency ?? []).find((candidate) => candidate.totalDebt?.currency === currency);
  const studentsInDebt = data?.studentsInDebt ?? 0;
  const studentsUpToDate = data?.studentsUpToDate ?? 0;

  return (
    <section aria-label="Hoy" className="flex min-w-0 flex-col gap-2">
      <h2 className="text-[11px] font-bold uppercase tracking-wide text-ink-soft">Hoy · todas las ofertas</h2>
      <div className={GRID}>
        <Stagger className="min-w-0">
          <StatCard label="Deuda total">
            <Value loading={isLoading} error={isError} onRetry={retry} tone="text-danger">
              {formatMoney(totals?.totalDebt) || '—'}
            </Value>
          </StatCard>
          <StatCard label="Alumnos con deuda vencida">
            <Value loading={isLoading} error={isError} onRetry={retry} tone="text-danger">
              {studentsInDebt}
            </Value>
            {!isError && !isLoading && <div className="mt-2 text-xs tabular-nums text-ink-soft">de {studentsInDebt + studentsUpToDate} · todas las monedas</div>}
          </StatCard>
          <StatCard label="Alumnos al día">
            <Value loading={isLoading} error={isError} onRetry={retry} tone="text-success-ink">
              {studentsUpToDate}
            </Value>
            {!isError && !isLoading && <div className="mt-2 text-xs text-ink-soft">todas las monedas</div>}
          </StatCard>
        </Stagger>
      </div>
    </section>
  );
}
