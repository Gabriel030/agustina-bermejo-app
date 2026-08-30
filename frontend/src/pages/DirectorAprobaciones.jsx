import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatCard } from '@/components/StatCard';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { useConfigsPendientes, useHistorialAprobaciones } from '@/hooks/useDirector';
import { ESTADO_CONFIG_LABEL, formatearFecha } from '@/lib/comisiones';

const TABS = [
  { id: 'pendientes', label: 'Pendientes' },
  { id: 'historial', label: 'Historial' },
];

function FilaPendiente({ c }) {
  return (
    <Link
      to={`/director/revisar-configuracion/${c.subjectSectionId}`}
      className="flex items-center justify-between gap-4 rounded-2xl px-4 py-3 transition-colors hover:bg-neutral-bg/60"
    >
      <div>
        <div className="font-heading text-[14px] font-semibold text-ink">
          {c.subjectName} · {c.sectionName}
        </div>
        <div className="mt-0.5 text-[12px] text-ink-faint">
          {c.instructors?.find((i) => i.role === 'LEAD')?.name ?? 'Sin titular'} (Titular) · enviada {formatearFecha(c.submittedAt) ?? '—'}
        </div>
      </div>
      <div className="flex items-center gap-3">
        <span className="rounded-full bg-warning-bg px-2.5 py-1 text-[11px] font-bold text-warning">Pendiente de aprobación</span>
        <ChevronRight className="h-4 w-4 text-ink-faint" />
      </div>
    </Link>
  );
}

function FilaHistorial({ c }) {
  const pill = ESTADO_CONFIG_LABEL[c.status] ?? { label: c.status, tone: 'neutral' };
  return (
    <Link
      to={`/director/revisar-configuracion/${c.subjectSectionId}`}
      className="flex items-center justify-between gap-4 rounded-2xl px-4 py-3 transition-colors hover:bg-neutral-bg/60"
    >
      <div>
        <div className="font-heading text-[14px] font-semibold text-ink">
          {c.subjectName} · {c.sectionName}
        </div>
        <div className="mt-0.5 text-[12px] text-ink-faint">
          {c.reviewedByName ? `Decidido por ${c.reviewedByName}` : 'Sin registro de quién decidió'} · {formatearFecha(c.reviewedAt) ?? '—'}
        </div>
        {c.status === 'REJECTED' && c.rejectionReason && (
          <div className="mt-0.5 text-[12px] font-semibold text-danger">Motivo: "{c.rejectionReason}"</div>
        )}
      </div>
      <div className="flex items-center gap-3">
        <StatusPill tone={pill.tone}>{pill.label}</StatusPill>
        <ChevronRight className="h-4 w-4 text-ink-faint" />
      </div>
    </Link>
  );
}

export function DirectorAprobaciones() {
  const [tab, setTab] = useState('pendientes');
  const { data: pendientes, isLoading: cargandoPendientes } = useConfigsPendientes();
  const { data: historial, isLoading: cargandoHistorial } = useHistorialAprobaciones();
  const listaPendientes = pendientes ?? [];

  return (
    <PageContainer>
      <div>
        <h1 className="font-heading text-2xl font-bold text-ink">Aprobaciones</h1>
        <p className="mt-0.5 text-sm text-ink-soft">Trámites esperando tu decisión, y lo que ya decidiste</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard label="Egresos por aprobar">
          <div className="font-heading text-2xl font-bold text-ink-faint">—</div>
          <div className="mt-1 text-[11px] text-ink-faint">Todavía no implementado</div>
        </StatCard>
        <StatCard label="Excepciones por decidir">
          <div className="font-heading text-2xl font-bold text-ink-faint">—</div>
          <div className="mt-1 text-[11px] text-ink-faint">Todavía no implementado</div>
        </StatCard>
        <StatCard label="Config. de evaluación pendientes">
          <div className="font-heading text-2xl font-bold text-ink">{listaPendientes.length}</div>
        </StatCard>
      </div>

      <div className="flex w-fit gap-1 rounded-xl border border-border bg-white/55 p-1">
        {TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={`rounded-lg px-4 py-1.5 text-[12.5px] font-bold ${
              tab === t.id ? 'bg-white text-primary shadow-sm' : 'text-ink-faint'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'pendientes' ? (
        <GlassCard className="flex flex-col gap-1 p-4">
          <div className="px-2 pb-2 font-heading text-[14.5px] font-semibold text-ink">Configuración de evaluación pendiente</div>
          {cargandoPendientes ? (
            <div className="flex flex-col gap-2 p-2">
              <Skeleton className="h-12" />
              <Skeleton className="h-12" />
            </div>
          ) : listaPendientes.length === 0 ? (
            <EmptyState title="Nada pendiente" className="py-6" />
          ) : (
            listaPendientes.map((c) => <FilaPendiente key={c.id} c={c} />)
          )}
        </GlassCard>
      ) : (
        <GlassCard className="flex flex-col gap-1 p-4">
          <div className="px-2 pb-2 font-heading text-[14.5px] font-semibold text-ink">Historial de decisiones</div>
          {cargandoHistorial ? (
            <div className="flex flex-col gap-2 p-2">
              <Skeleton className="h-12" />
              <Skeleton className="h-12" />
            </div>
          ) : historial.length === 0 ? (
            <EmptyState title="Todavía no decidiste nada" description="Acá vas a ver lo que aprobaste o rechazaste." className="py-6" />
          ) : (
            historial.map((c) => <FilaHistorial key={c.id} c={c} />)
          )}
        </GlassCard>
      )}
    </PageContainer>
  );
}
