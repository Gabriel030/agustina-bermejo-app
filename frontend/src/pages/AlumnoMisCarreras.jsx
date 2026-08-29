import { useSearchParams } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatCard } from '@/components/StatCard';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Select, SelectTrigger, SelectContent, SelectItem, SelectValue } from '@/components/ui/select';
import { TabMaterias } from '@/components/alumno/TabMaterias';
import { TabPlanDeEstudio } from '@/components/alumno/TabPlanDeEstudio';
import { TabNotas } from '@/components/alumno/TabNotas';
import { TabAsistencia } from '@/components/alumno/TabAsistencia';
import { TabMesasDeExamen } from '@/components/alumno/TabMesasDeExamen';
import { useMisMatriculaciones, useMiProgreso } from '@/hooks/useAlumno';
import { useProgramaPlanDeEstudio } from '@/hooks/useCatalogo';
import { useMateriasDelPlan } from '@/hooks/useMateriasDelPlan';

// Épica 2 Fase 2 — consolidación de IA (pedido explícito del usuario, "menos
// botones en el menu, y queda mas acoplado a la materia"): Materias, Notas,
// Mesas de examen y Asistencia dejaron de ser ítems de nav de primer nivel —
// ahora son pestañas DENTRO de Mis carreras, siempre en el contexto de la
// carrera elegida arriba. Reemplaza el layout anterior de 2 cards resumen
// (Materias aprobadas/en curso) — esa info ahora vive completa en la pestaña
// Materias, no hace falta duplicarla acá.
const TABS = [
  { id: 'materias', label: 'Materias' },
  { id: 'notas', label: 'Notas' },
  { id: 'mesas-de-examen', label: 'Mesas de examen' },
  { id: 'asistencia', label: 'Asistencia' },
  { id: 'plan-de-estudio', label: 'Plan de estudio' },
];

export function AlumnoMisCarreras() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { data: matriculaciones, isLoading: cargandoMatriculaciones } = useMisMatriculaciones();

  const activas = (matriculaciones ?? []).filter((m) => m.status !== 'WITHDRAWN' && m.status !== 'COMPLETED');
  const carreraParam = searchParams.get('carrera');
  const matricula = activas.find((m) => m.id === Number(carreraParam)) ?? activas[0];
  const tab = searchParams.get('tab') ?? 'materias';

  const { data: progreso } = useMiProgreso(matricula?.id);
  const { data: planDeEstudio } = useProgramaPlanDeEstudio(matricula?.programId);
  const totalMateriasPlan = planDeEstudio?.length;
  // Mismo cruce plan+inscripciones que usan las pestañas Materias/Notas (React Query
  // dedupea el fetch por queryKey, no dispara requests extra) — solo se usa acá para el
  // conteo de aprobadas del resumen de arriba.
  const { filas: materiasClasificadas } = useMateriasDelPlan(matricula?.id);
  const aprobadasCount = materiasClasificadas.filter((f) => f.estado === 'aprobada').length;
  const porcentajeAvance =
    progreso?.creditosRequeridos ? Math.round((progreso.creditosAcumulados / progreso.creditosRequeridos) * 100) : 0;

  function cambiarCarrera(value) {
    searchParams.set('carrera', value);
    searchParams.delete('estado');
    setSearchParams(searchParams, { replace: true });
  }

  function cambiarTab(nuevoTab) {
    searchParams.set('tab', nuevoTab);
    searchParams.delete('estado');
    setSearchParams(searchParams, { replace: true });
  }

  if (cargandoMatriculaciones) {
    return (
      <PageContainer>
        <div className="flex items-center justify-between gap-4">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-10 w-56" />
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Skeleton className="h-20" />
          <Skeleton className="h-20" />
          <Skeleton className="h-20" />
        </div>
        <Skeleton className="h-64" />
      </PageContainer>
    );
  }

  if (activas.length === 0) {
    return (
      <PageContainer>
        <GlassCard className="flex flex-1 flex-col p-12">
          <EmptyState
            title="Todavía no estás matriculado en ninguna carrera"
            description="Elegí una carrera en el Catálogo para solicitar tu alta."
            action={
              <Button asChild variant="gradient">
                <a href="/catalogo?tab=carreras">Ir al Catálogo</a>
              </Button>
            }
          />
        </GlassCard>
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <div className="flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center">
        <div>
          <div className="flex items-center gap-2.5">
            <div className="font-heading text-2xl font-bold tracking-tight text-ink">Mis carreras</div>
            {progreso && (
              <StatusPill tone={progreso.esRegular ? 'success' : 'neutral'}>
                {progreso.esRegular ? 'Alumno regular' : 'No estás cursando'}
              </StatusPill>
            )}
          </div>
          <div className="mt-1 text-sm text-ink-faint">Progreso del plan de estudios</div>
        </div>
        {/* Siempre visible, aunque haya una sola carrera — predecible, no aparece/desaparece
            según cuántas carreras tenga el alumno (decisión de producto). En la URL
            (?carrera=) para que el breadcrumb pueda volver acá sin perder la carrera elegida. */}
        <Select value={String(matricula?.id ?? '')} onValueChange={cambiarCarrera}>
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {activas.map((m) => (
              <SelectItem key={m.id} value={String(m.id)}>
                {m.programName}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard label="Materias aprobadas">
          <div className="font-heading text-2xl font-bold text-ink">
            {aprobadasCount}
            {totalMateriasPlan != null && <span className="text-base font-semibold text-ink-faint"> / {totalMateriasPlan}</span>}
          </div>
        </StatCard>
        <StatCard label="Créditos acumulados">
          <div className="font-heading text-2xl font-bold text-ink">
            {progreso?.creditosAcumulados ?? '—'}
            {progreso?.creditosRequeridos != null && (
              <span className="text-base font-semibold text-ink-faint"> / {progreso.creditosRequeridos}</span>
            )}
          </div>
        </StatCard>
        <StatCard label="Avance del plan">
          <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-neutral-bg">
            <div className="h-full rounded-full bg-gradient-to-r from-accent to-accent-2" style={{ width: `${porcentajeAvance}%` }} />
          </div>
        </StatCard>
      </div>

      <div className="flex gap-1 overflow-x-auto rounded-xl border border-border bg-surface p-1">
        {TABS.map(({ id, label }) => (
          <button
            key={id}
            type="button"
            onClick={() => cambiarTab(id)}
            className={`whitespace-nowrap rounded-lg px-4 py-2 text-[13px] font-semibold transition-colors ${
              tab === id ? 'bg-gradient-to-br from-accent to-accent-2 text-white' : 'text-ink-soft hover:bg-neutral-bg'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {/* key={matricula.id}: fuerza remount al cambiar de carrera, para que la página
          interna de paginación de cada pestaña arranque de nuevo (si no, quedarse en
          la página 3 de una carrera y cambiar a otra con menos materias muestra una
          página vacía en vez de resetear). */}
      {tab === 'materias' && <TabMaterias key={matricula.id} carreraId={matricula.id} />}
      {tab === 'plan-de-estudio' && <TabPlanDeEstudio key={matricula.id} carreraId={matricula.id} />}
      {tab === 'notas' && <TabNotas key={matricula.id} carreraId={matricula.id} />}
      {tab === 'asistencia' && <TabAsistencia key={matricula.id} carreraId={matricula.id} />}
      {tab === 'mesas-de-examen' && <TabMesasDeExamen key={matricula.id} carreraId={matricula.id} />}
    </PageContainer>
  );
}
