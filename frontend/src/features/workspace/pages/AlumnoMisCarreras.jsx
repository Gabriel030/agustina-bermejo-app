import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle2, Star, TrendingUp } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatCard } from '@/components/StatCard';
import { Stagger } from '@/components/Stagger';
import { ProgressBar } from '@/components/ProgressBar';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Button } from '@/components/ui/button';
import { Select, SelectTrigger, SelectContent, SelectItem, SelectValue } from '@/components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { TabTramites } from '@/features/workspace/components/TabTramites';
import { TabMaterias } from '@/features/workspace/components/TabMaterias';
import { TabPlanDeEstudio } from '@/features/workspace/components/TabPlanDeEstudio';
import { TabNotas } from '@/features/evaluation';
import { TabAsistencia } from '@/features/attendance';
import { TabMesasDeExamen } from '@/features/evaluation';
import { GraduationRequestAction } from '@/features/graduation';
import { describeRegularity, useMyRegularity } from '@/features/identity';
import { useMisMatriculaciones, useMiProgreso, useCurriculumProgress } from '@/features/enrollment';

function StatLabel({ icon: Icon, children }) {
  return (
    <span className="flex items-center gap-1.5">
      <Icon className="h-3.5 w-3.5" aria-hidden="true" />
      {children}
    </span>
  );
}

export function AlumnoMisCarreras() {
  const [searchParams, setSearchParams] = useSearchParams();
  const {
    data: matriculaciones,
    isLoading: cargandoMatriculaciones,
    isError: matriculacionesFallaron,
    error: errorMatriculaciones,
    refetch: recargarMatriculaciones,
  } = useMisMatriculaciones();

  const activas = (matriculaciones ?? [])
    .filter((m) => m.status !== 'WITHDRAWN')
    .sort((a, b) => Number(a.status === 'COMPLETED') - Number(b.status === 'COMPLETED'));
  const carreraParam = searchParams.get('carrera');
  const matricula = activas.find((m) => m.id === Number(carreraParam)) ?? activas[0];
  const graduated = matricula?.status === 'COMPLETED';
  const requestedTab = searchParams.get('tab') ?? 'materias';
  const tab = requestedTab === 'egreso' ? 'tramites' : requestedTab;

  const { data: progreso } = useMiProgreso(matricula?.id);
  const { data: regularity } = useMyRegularity('PROGRAM', graduated ? null : matricula?.id);
  const regularityView = regularity ? describeRegularity(regularity) : null;
  const { data: curriculum } = useCurriculumProgress(matricula?.id);
  const summary = curriculum?.summary;
  const aprobadasCount = summary?.counts.approved;
  const totalMateriasPlan = curriculum?.subjects.length;
  const porcentajeAvance =
    summary?.completionPercent ?? (summary?.mandatoryTotal ? (summary.mandatoryApproved / summary.mandatoryTotal) * 100 : 0);

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
          <Skeleton className="h-11 w-56" />
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
        <Skeleton className="h-11 w-3/4" />
        <Skeleton className="h-72" />
      </PageContainer>
    );
  }

  if (matriculacionesFallaron) {
    return (
      <PageContainer>
        <LoadErrorState error={errorMatriculaciones} onRetry={recargarMatriculaciones} resource="tus carreras" distinguishForbidden backTo="/alumno/inicio" backLabel="Inicio" />
      </PageContainer>
    );
  }

  if (activas.length === 0) {
    return (
      <PageContainer>
        <h1 className="font-heading text-2xl font-bold tracking-tight text-ink">Mis carreras</h1>
        <GlassCard className="flex flex-1 flex-col p-12">
          <EmptyState
            icon="🎓"
            title="Todavía no estás matriculado en ninguna carrera"
            description="Elegí una carrera en el Catálogo para solicitar tu alta."
            action={
              <Button asChild variant="primary" className="h-11 px-4">
                <Link to="/catalogo?tab=carreras">Ir al Catálogo</Link>
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
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="font-heading text-2xl font-bold tracking-tight text-ink">Mis carreras</h1>
            {graduated && (
              <StatusPill tone="success">
                <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
                Carrera finalizada
              </StatusPill>
            )}
            {progreso && !graduated && (
              <StatusPill tone={progreso.hasActiveCoursework ? 'success' : 'neutral'}>
                {progreso.hasActiveCoursework && <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />}
                {progreso.hasActiveCoursework ? 'Con cursada activa' : 'Sin cursada activa'}
              </StatusPill>
            )}
          </div>
          {regularityView && (
            <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1">
              <StatusPill tone={regularityView.tone}>{regularityView.label}</StatusPill>
              {regularityView.details.length > 0 && (
                <span className="text-size-12 text-ink-faint">{regularityView.details.join(' · ')}</span>
              )}
            </div>
          )}
          <div className="mt-1 text-sm text-ink-faint">Progreso del plan de estudios</div>
        </div>
        <div className="flex w-full flex-col gap-2 sm:w-auto sm:flex-row sm:items-center">
          {tab === 'tramites' && <GraduationRequestAction key={matricula.id} carreraId={matricula.id} />}
          <Select value={String(matricula?.id ?? '')} onValueChange={cambiarCarrera}>
            <SelectTrigger aria-label="Carrera" className="w-full data-[size=default]:h-11 sm:w-auto">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {activas.map((m) => (
                <SelectItem key={m.id} value={String(m.id)}>
                  {m.status === 'COMPLETED' ? `${m.programName} (finalizada)` : m.programName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Stagger>
          <StatCard label={<StatLabel icon={CheckCircle2}>Materias aprobadas</StatLabel>}>
            <div className="font-heading text-2xl font-bold text-ink">
              {aprobadasCount ?? '—'}
              {totalMateriasPlan != null && <span className="text-base font-semibold text-ink-faint"> / {totalMateriasPlan}</span>}
            </div>
          </StatCard>
          <StatCard label={<StatLabel icon={Star}>Créditos acumulados</StatLabel>}>
            <div className="font-heading text-2xl font-bold text-ink">
              {summary && summary.requiredCredits > 0 ? summary.accumulatedCredits : '—'}
              {summary?.requiredCredits > 0 && (
                <span className="text-base font-semibold text-ink-faint"> / {summary.requiredCredits}</span>
              )}
            </div>
          </StatCard>
          <StatCard label={<StatLabel icon={TrendingUp}>Avance del plan</StatLabel>}>
            <ProgressBar value={porcentajeAvance} label="Avance del plan" className="mt-1.5" />
          </StatCard>
        </Stagger>
      </div>

      <Tabs value={tab} onValueChange={cambiarTab}>
        <TabsList variant="segmented">
          <TabsTrigger value="materias">Materias</TabsTrigger>
          <TabsTrigger value="notas">Notas</TabsTrigger>
          <TabsTrigger value="mesas-de-examen">Mesas de examen</TabsTrigger>
          <TabsTrigger value="asistencia">Asistencia</TabsTrigger>
          <TabsTrigger value="plan-de-estudio">Plan de estudio</TabsTrigger>
          <TabsTrigger value="tramites">Trámites</TabsTrigger>
        </TabsList>
        <TabsContent value={tab} className="mt-4 flex flex-1 flex-col">
          {tab === 'materias' && <TabMaterias key={matricula.id} carreraId={matricula.id} />}
          {tab === 'plan-de-estudio' && <TabPlanDeEstudio key={matricula.id} carreraId={matricula.id} />}
          {tab === 'notas' && <TabNotas key={matricula.id} carreraId={matricula.id} />}
          {tab === 'asistencia' && <TabAsistencia key={matricula.id} carreraId={matricula.id} />}
          {tab === 'mesas-de-examen' && <TabMesasDeExamen key={matricula.id} carreraId={matricula.id} />}
          {tab === 'tramites' && <TabTramites key={matricula.id} carreraId={matricula.id} onSeeStudyPlan={() => cambiarTab('plan-de-estudio')} />}
        </TabsContent>
      </Tabs>
    </PageContainer>
  );
}
