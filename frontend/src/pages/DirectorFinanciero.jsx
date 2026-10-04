import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { TrendingDown, TrendingUp, Users, AlertCircle } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatCard } from '@/components/StatCard';
import { Stagger } from '@/components/Stagger';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { useFinancialDashboard, useDebtors } from '@/hooks/useDirector';
import { formatMoney } from '@/lib/utils';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';

function formatPesos(money) {
  return formatMoney(money) || '—';
}

const COUNT_GRID = 'grid gap-4 [grid-template-columns:repeat(auto-fit,minmax(10rem,1fr))]';
const AMOUNT_GRID = 'grid gap-4 [grid-template-columns:repeat(auto-fit,minmax(15rem,1fr))]';

function CurrencyKpis({ totals, currencyLabel }) {
  return (
    <section aria-label={currencyLabel ?? 'Montos'} className="flex min-w-0 flex-col gap-2">
      {currencyLabel && (
        <h2 className="text-[11px] font-bold uppercase tracking-wide text-ink-faint">{currencyLabel}</h2>
      )}
      <div className={AMOUNT_GRID}>
        <Stagger className="min-w-0">
          <StatCard label="Deuda total">
            <div className="font-heading text-2xl font-bold text-danger">{formatPesos(totals.totalDebt)}</div>
          </StatCard>
          <StatCard label="Proyectado del mes">
            <div className="font-heading text-2xl font-bold text-ink">{formatPesos(totals.monthlyProjected)}</div>
          </StatCard>
          <StatCard label="Cobrado del mes">
            <div className="font-heading text-2xl font-bold text-success">{formatPesos(totals.monthlyCollected)}</div>
          </StatCard>
          <StatCard label="Pendiente del mes">
            <div className="font-heading text-2xl font-bold text-warning">{formatPesos(totals.monthlyDebt)}</div>
          </StatCard>
        </Stagger>
      </div>
    </section>
  );
}

function KpiCards({ data, isLoading }) {
  if (isLoading) {
    return (
      <div className={COUNT_GRID}>
        {Array.from({ length: 4 }).map((_, i) => (
          <GlassCard key={i} className="p-5">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="mt-2 h-8 w-20" />
          </GlassCard>
        ))}
      </div>
    );
  }

  const totalsByCurrency = data?.totalsByCurrency ?? [];
  const multiCurrency = totalsByCurrency.length > 1;

  return (
    <div className="flex min-w-0 flex-col gap-6">
      <div className={COUNT_GRID}>
        <Stagger className="min-w-0">
          <StatCard label="Alumnos en deuda">
            <span className="font-heading text-3xl font-bold text-danger">{data?.studentsInDebt ?? 0}</span>
          </StatCard>
          <StatCard label="Alumnos al día">
            <span className="font-heading text-3xl font-bold text-success">{data?.studentsUpToDate ?? 0}</span>
          </StatCard>
        </Stagger>
      </div>
      {(totalsByCurrency.length ? totalsByCurrency : [{}]).map((totals) => (
        <CurrencyKpis
          key={totals.totalDebt?.currency ?? 'none'}
          totals={totals}
          currencyLabel={multiCurrency ? totals.totalDebt.currency : null}
        />
      ))}
    </div>
  );
}

const SORT_OPTIONS = [
  { value: 'DEBT_AGE', label: 'Antigüedad de deuda' },
  { value: 'DEBT_AMOUNT', label: 'Monto de deuda' },
];

function DebtorsTable({ data, isLoading }) {
  const navigate = useNavigate();

  if (isLoading) {
    return (
      <div className="flex flex-col gap-2 p-4">
        {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-12" />)}
      </div>
    );
  }

  if (!data?.length) {
    return <EmptyState title="Sin alumnos con deuda vencida" className="py-10" />;
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="pl-4">Alumno</TableHead>
          <TableHead>DNI</TableHead>
          <TableHead>Inscripción</TableHead>
          <TableHead className="text-right">Deuda</TableHead>
          <TableHead className="pr-4 text-right">Vencida hace</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {data.map((d) => (
          <TableRow key={`${d.studentId}-${d.totalDebt.currency}`}className="cursor-pointer" onClick={() => navigate(`/director/alumnos/${d.studentId}`)}>
            <TableCell className="pl-4 font-medium text-ink">{d.fullName}</TableCell>
            <TableCell className="text-ink-soft">{d.dni}</TableCell>
            <TableCell className="text-ink-soft">{d.enrollmentName}</TableCell>
            <TableCell className="text-right font-semibold text-danger">{formatPesos(d.totalDebt)}</TableCell>
            <TableCell className="pr-4 text-right text-ink-soft">
              {d.debtAgeDays === 0 ? 'Hoy' : `${d.debtAgeDays} día${d.debtAgeDays !== 1 ? 's' : ''}`}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export function DirectorFinanciero() {
  const [sortBy, setSortBy] = useState('DEBT_AGE');
  const { data: kpis, isLoading: cargandoKpis } = useFinancialDashboard();
  const { data: debtors, isLoading: cargandoDeudores } = useDebtors(sortBy);

  return (
    <PageContainer title="Resumen financiero">
      <p className="-mt-4 text-sm text-ink-soft">Estado de cobros y alumnos con deuda vencida</p>

      <KpiCards data={kpis} isLoading={cargandoKpis} />

      <Link to="/director/financiero/anios-lectivos" className="text-sm font-semibold text-primary hover:underline">
        Configurar el cobro de los años lectivos por carrera
      </Link>

      <GlassCard>
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <div className="flex items-center gap-2">
            <AlertCircle className="h-4 w-4 text-danger" />
            <span className="font-heading text-[14.5px] font-semibold text-ink">Alumnos con deuda vencida</span>
          </div>
          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value)}
            className="rounded-md border border-border bg-surface px-2.5 py-1.5 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-primary/40"
          >
            {SORT_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
        </div>
        <DebtorsTable data={debtors} isLoading={cargandoDeudores} />
      </GlassCard>
    </PageContainer>
  );
}
