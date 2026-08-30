import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatCard } from '@/components/StatCard';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { useMisComisiones } from '@/hooks/useDocente';
import { agruparComisionesPorFase, diasHastaVencimientoMasProximo, formatearFecha, ESTADO_CONFIG_LABEL } from '@/lib/comisiones';

function FilaComision({ comision }) {
  const pill = ESTADO_CONFIG_LABEL[comision.status] ?? { label: comision.status, tone: 'neutral' };
  const titular = comision.instructors?.find((i) => i.role === 'LEAD');
  const coDictantes = comision.instructors?.filter((i) => i.role !== 'LEAD') ?? [];

  return (
    <Link
      to={`/docente/mis-comisiones/${comision.subjectSectionId}`}
      className="flex items-center justify-between gap-4 rounded-2xl px-4 py-3 transition-colors hover:bg-neutral-bg/60"
    >
      <div className="min-w-0">
        <div className="font-heading text-[13.5px] font-semibold text-ink">
          {comision.subjectName} · {comision.sectionName}
        </div>
        {coDictantes.length > 0 && (
          <div className="mt-0.5 text-[11px] text-ink-faint">Co-dictada con {coDictantes.map((i) => i.name).join(', ')}</div>
        )}
      </div>
      <div className="flex shrink-0 items-center gap-4">
        <span className="text-[12px] text-ink-faint">{formatearFecha(comision.termStartDate) ?? '—'}</span>
        <StatusPill tone={pill.tone}>{pill.label}</StatusPill>
        <ChevronRight className="h-4 w-4 text-ink-faint" />
      </div>
    </Link>
  );
}

function SeccionComisiones({ titulo, comisiones, vacio }) {
  if (comisiones.length === 0) return null;
  return (
    <GlassCard className="flex flex-col gap-1 p-4">
      <div className="px-2 pb-2 font-heading text-[14.5px] font-semibold text-ink">
        {titulo} <span className="font-normal text-ink-faint">{comisiones.length}</span>
      </div>
      {comisiones.length === 0 ? (
        <EmptyState title={vacio} className="py-4" />
      ) : (
        comisiones.map((c) => <FilaComision key={c.id} comision={c} />)
      )}
    </GlassCard>
  );
}

export function DocenteMisComisiones() {
  const { data: comisiones, isLoading } = useMisComisiones();

  if (isLoading) {
    return (
      <PageContainer>
        <div className="flex flex-col gap-2">
          <Skeleton className="h-10" />
          <Skeleton className="h-10" />
          <Skeleton className="h-10" />
        </div>
      </PageContainer>
    );
  }

  const lista = comisiones ?? [];
  const { bandejaDeAprobacion, enCurso, cerradas } = agruparComisionesPorFase(lista);
  const diasVencimiento = diasHastaVencimientoMasProximo(lista);

  return (
    <PageContainer>
      <div>
        <h1 className="font-heading text-2xl font-bold text-ink">Mis comisiones</h1>
        <p className="mt-0.5 text-sm text-ink-soft">Configuración de evaluación y mesas</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard label="Comisiones este período">
          <div className="font-heading text-2xl font-bold text-ink">{lista.length}</div>
        </StatCard>
        <StatCard label="Pendientes de completar/aprobar">
          <div className="font-heading text-2xl font-bold text-ink">{bandejaDeAprobacion.length}</div>
        </StatCard>
        <StatCard label="Días para el vencimiento más próximo">
          <div className={`font-heading text-2xl font-bold ${diasVencimiento !== null && diasVencimiento <= 5 ? 'text-danger' : 'text-ink'}`}>
            {diasVencimiento ?? '—'}
          </div>
        </StatCard>
      </div>

      {lista.length === 0 ? (
        <GlassCard className="p-4">
          <EmptyState title="Todavía no tenés comisiones asignadas" description="Cuando Dirección te asigne una comisión, aparece acá." />
        </GlassCard>
      ) : (
        <div className="flex flex-col gap-4">
          <SeccionComisiones titulo="Bandeja de aprobación" comisiones={bandejaDeAprobacion} vacio="Nada pendiente" />
          <SeccionComisiones titulo="En curso" comisiones={enCurso} vacio="Nada en curso" />
          <SeccionComisiones titulo="Cerradas" comisiones={cerradas} vacio="Nada cerrado todavía" />
        </div>
      )}
    </PageContainer>
  );
}
