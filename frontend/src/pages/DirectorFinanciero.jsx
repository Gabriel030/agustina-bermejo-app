import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TrendingDown, TrendingUp, Users, AlertCircle } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatCard } from '@/components/StatCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { useFinancialDashboard, useDebtors } from '@/hooks/useDirector';

function formatPesos(amount) {
  if (amount == null) return '—';
  return new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 }).format(amount);
}

function KpiCards({ data, isLoading }) {
  if (isLoading) {
    return (
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
        {Array.from({ length: 6 }).map((_, i) => (
          <GlassCard key={i} className="p-5">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="mt-2 h-8 w-20" />
          </GlassCard>
        ))}
      </div>
    );
  }

  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
      <StatCard label="Alumnos en deuda">
        <div className="flex items-end gap-1.5">
          <span className="font-heading text-3xl font-bold text-danger">{data?.studentsInDebt ?? 0}</span>
        </div>
      </StatCard>
      <StatCard label="Alumnos al día">
        <div className="font-heading text-3xl font-bold text-success">{data?.studentsUpToDate ?? 0}</div>
      </StatCard>
      <StatCard label="Deuda total">
        <div className="font-heading text-2xl font-bold text-danger">{formatPesos(data?.totalDebt)}</div>
      </StatCard>
      <StatCard label="Proyectado del mes">
        <div className="font-heading text-2xl font-bold text-ink">{formatPesos(data?.monthlyProjected)}</div>
      </StatCard>
      <StatCard label="Cobrado del mes">
        <div className="font-heading text-2xl font-bold text-success">{formatPesos(data?.monthlyCollected)}</div>
      </StatCard>
      <StatCard label="Pendiente del mes">
        <div className="font-heading text-2xl font-bold text-warning">{formatPesos(data?.monthlyDebt)}</div>
      </StatCard>
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
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border">
            <th className="py-2 pl-4 text-left text-[11px] font-bold uppercase tracking-wide text-ink-faint">Alumno</th>
            <th className="py-2 text-left text-[11px] font-bold uppercase tracking-wide text-ink-faint">DNI</th>
            <th className="py-2 text-left text-[11px] font-bold uppercase tracking-wide text-ink-faint">Inscripción</th>
            <th className="py-2 text-right text-[11px] font-bold uppercase tracking-wide text-ink-faint">Deuda</th>
            <th className="py-2 pr-4 text-right text-[11px] font-bold uppercase tracking-wide text-ink-faint">Vencida hace</th>
          </tr>
        </thead>
        <tbody>
          {data.map((d) => (
            <tr
              key={d.studentId}
              className="cursor-pointer border-b border-border/60 transition-colors last:border-0 hover:bg-neutral-bg/40"
              onClick={() => navigate(`/director/alumnos/${d.studentId}`)}
            >
              <td className="py-3 pl-4 font-medium text-ink">{d.fullName}</td>
              <td className="py-3 text-ink-soft">{d.dni}</td>
              <td className="py-3 text-ink-soft">{d.enrollmentName}</td>
              <td className="py-3 text-right font-semibold text-danger">{formatPesos(d.totalDebt)}</td>
              <td className="py-3 pr-4 text-right text-ink-soft">
                {d.debtAgeDays === 0 ? 'Hoy' : `${d.debtAgeDays} día${d.debtAgeDays !== 1 ? 's' : ''}`}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function DirectorFinanciero() {
  const [sortBy, setSortBy] = useState('DEBT_AGE');
  const { data: kpis, isLoading: cargandoKpis } = useFinancialDashboard();
  const { data: debtors, isLoading: cargandoDeudores } = useDebtors(sortBy);

  return (
    <PageContainer>
      <div className="mb-6">
        <h1 className="font-heading text-2xl font-bold text-ink">Resumen financiero</h1>
        <p className="mt-0.5 text-sm text-ink-soft">Estado de cobros y alumnos con deuda vencida</p>
      </div>

      <KpiCards data={kpis} isLoading={cargandoKpis} />

      <GlassCard className="mt-6">
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
