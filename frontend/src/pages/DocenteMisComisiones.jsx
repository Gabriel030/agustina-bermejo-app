import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatCard } from '@/components/StatCard';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { useComisionesDictadas, useMisComisiones } from '@/hooks/useDocente';
import { agruparComisionesPorFase, diasHastaVencimientoMasProximo, formatearFecha, ESTADO_CONFIG_LABEL } from '@/lib/comisiones';

function FilaComision({ comision }) {
  const esCurso = comision.type === 'COURSE';
  const pill = esCurso || !comision.status ? null : (ESTADO_CONFIG_LABEL[comision.status] ?? { label: comision.status, tone: 'neutral' });
  const coDictantes = comision.instructors?.filter((i) => i.role !== 'LEAD') ?? [];
  const destino = esCurso
    ? `/docente/mis-comisiones/${comision.sectionId}?type=COURSE`
    : `/docente/mis-comisiones/${comision.sectionId}`;

  return (
    <Link
      to={destino}
      className="flex flex-col gap-2 rounded-2xl px-4 py-3 transition-colors hover:bg-neutral-bg/60 sm:flex-row sm:items-center sm:justify-between sm:gap-4"
    >
      <div className="min-w-0">
        <div className="font-heading text-[13.5px] font-semibold text-ink">
          {comision.subjectName} · {comision.sectionName}{comision.termStartDate ? ` (${new Date(comision.termStartDate).getFullYear()})` : ''}
        </div>
        {coDictantes.length > 0 && (
          <div className="mt-0.5 text-[11px] text-ink-faint">Co-dictada con {coDictantes.map((i) => i.name).join(', ')}</div>
        )}
      </div>
      <div className="flex flex-wrap items-center gap-2 sm:shrink-0 sm:flex-nowrap sm:gap-4">
        <StatusPill tone="neutral">{esCurso ? 'Curso' : 'Carrera'}</StatusPill>
        <span className="text-[12px] text-ink-faint">{formatearFecha(comision.termStartDate) ?? '—'}</span>
        {pill && <StatusPill tone={pill.tone}>{pill.label}</StatusPill>}
        <ChevronRight className="ml-auto h-4 w-4 text-ink-faint sm:ml-0" />
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

// `/me/section-evaluation-configs` solo trae materias de carrera y es el único que
// sabe el estado de la configuración y los co-dictantes; `/me/teaching-sections` trae
// además los cursos sueltos pero sin esos datos. Se mergean por sección para que un
// docente que solo dicta cursos deje de ver la pantalla vacía, sin perder el estado ni
// las métricas de las materias de carrera.
function unificarComisiones(configs, dictadas) {
  const porSeccion = new Map();
  for (const config of configs) {
    porSeccion.set(`PROGRAM-${config.subjectSectionId}`, {
      ...config,
      type: 'PROGRAM',
      sectionId: config.subjectSectionId,
    });
  }
  for (const dictada of dictadas) {
    const clave = `${dictada.type}-${dictada.id}`;
    if (porSeccion.has(clave)) continue;
    porSeccion.set(clave, { ...dictada, sectionId: dictada.id, id: clave });
  }
  return [...porSeccion.values()];
}

export function DocenteMisComisiones() {
  const { data: configs, isLoading, isError, refetch } = useMisComisiones();
  const { data: dictadas, isLoading: cargandoDictadas, isError: errorDictadas, refetch: refetchDictadas } = useComisionesDictadas();

  if (isLoading || cargandoDictadas) {
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

  if (isError || errorDictadas) {
    return (
      <PageContainer>
        <h1 className="font-heading text-2xl font-bold text-ink">Mis comisiones</h1>
        <GlassCard className="p-4">
          <EmptyState
            icon="⚠️"
            title="No pudimos cargar tus comisiones"
            description="Puede ser un problema de conexión. Probá de nuevo en un momento."
            action={
              <Button
                variant="outline"
                onClick={() => {
                  refetch();
                  refetchDictadas();
                }}
              >
                Reintentar
              </Button>
            }
          />
        </GlassCard>
      </PageContainer>
    );
  }

  const lista = unificarComisiones(configs ?? [], dictadas ?? []);
  const { bandejaDeAprobacion, enCurso, cerradas } = agruparComisionesPorFase(lista);
  const vigentes = lista.filter((c) => !cerradas.includes(c));
  const diasVencimiento = diasHastaVencimientoMasProximo(vigentes);
  const vencido = diasVencimiento === 'vencido';

  return (
    <PageContainer>
      <div>
        <h1 className="font-heading text-2xl font-bold text-ink">Mis comisiones</h1>
        <p className="mt-0.5 text-sm text-ink-soft">Evaluación, mesas y avisos de tus comisiones</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard label="Comisiones y cursos este período">
          <div className="font-heading text-2xl font-bold text-ink">{vigentes.length}</div>
        </StatCard>
        <StatCard label="Pendientes de completar/aprobar">
          <div className="font-heading text-2xl font-bold text-ink">{bandejaDeAprobacion.length}</div>
        </StatCard>
        <StatCard label="Días para el vencimiento más próximo">
          <div className={`font-heading text-2xl font-bold ${vencido || (diasVencimiento !== null && diasVencimiento <= 5) ? 'text-danger' : 'text-ink'}`}>
            {vencido ? 'Vencido' : (diasVencimiento ?? '—')}
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
