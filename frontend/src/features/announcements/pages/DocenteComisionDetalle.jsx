import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { DocenteConfiguracionEvaluacion } from '@/features/evaluation';
import { useComisionesDictadas } from '@/features/academic-structure';
import { useConfiguracionComision } from '@/features/evaluation';
import { comisionCerrada, ESTADO_CONFIG_LABEL, ROL_INSTRUCTOR_LABEL } from '@/lib/comisiones';
import { TablaAvisos } from '../components/TablaAvisos';
import { useAvisos } from '../hooks/useAvisos';
import { useEliminarAviso } from '../hooks/useEliminarAviso';

export function DocenteComisionDetalle() {
  const { sectionId } = useParams();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const tipo = searchParams.get('type') === 'COURSE' ? 'COURSE' : 'PROGRAM';
  const esCurso = tipo === 'COURSE';
  const tab = esCurso ? 'avisos' : (searchParams.get('tab') ?? 'evaluacion');

  const { data: config, isLoading: cargandoConfig, isError: sinConfig } = useConfiguracionComision(esCurso ? undefined : sectionId);
  const { data: dictadas, isLoading: cargandoDictadas } = useComisionesDictadas();
  const avisos = useAvisos(sectionId, tipo);
  const eliminarAviso = useEliminarAviso(sectionId, tipo);

  const dictada = (dictadas ?? []).find((s) => s.type === tipo && String(s.id) === sectionId);
  const cargandoEncabezado = esCurso ? cargandoDictadas : cargandoConfig || (sinConfig && cargandoDictadas);

  if (cargandoEncabezado) {
    return (
      <PageContainer>
        <Skeleton className="h-10" />
        <Skeleton className="h-40" />
      </PageContainer>
    );
  }

  const encabezado = esCurso || sinConfig ? dictada : config;

  if (!encabezado) {
    return (
      <PageContainer>
        <GlassCard className="p-4">
          <EmptyState
            icon="🔎"
            title="No encontramos esta comisión"
            description="Puede que no exista o que no esté asignada a vos."
            action={
              <Button variant="outline" asChild>
                <Link to="/docente/mis-comisiones">Volver a Mis comisiones</Link>
              </Button>
            }
          />
        </GlassCard>
      </PageContainer>
    );
  }

  const nombre = [encabezado.subjectName, encabezado.sectionName].filter(Boolean).join(' · ');
  const pill = esCurso || sinConfig ? null : (ESTADO_CONFIG_LABEL[config.status] ?? { label: config.status, tone: 'neutral' });
  const cerrada = esCurso ? false : comisionCerrada(encabezado.termEndDate);

  const sufijoTipo = esCurso ? '?type=COURSE' : '';

  function irANuevoAviso() {
    navigate(`/docente/mis-comisiones/${sectionId}/avisos/nuevo${sufijoTipo}`);
  }

  function irAEditar(aviso) {
    navigate(`/docente/mis-comisiones/${sectionId}/avisos/${aviso.id}/editar${sufijoTipo}`);
  }

  function cambiarTab(nuevoTab) {
    searchParams.set('tab', nuevoTab);
    setSearchParams(searchParams, { replace: true });
  }

  return (
    <PageContainer>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="font-heading text-2xl font-bold text-ink">{nombre}</h1>
            {pill && <StatusPill tone={pill.tone}>{pill.label}</StatusPill>}
          </div>
          {config?.instructors?.length > 0 && (
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <span className="text-[11.5px] font-semibold text-ink-faint">Instructores:</span>
              {config.instructors.map((i) => (
                <span key={i.name} className="rounded-full bg-accent-soft px-2.5 py-1 text-[11.5px] font-bold text-primary">
                  {i.name} <span className="font-semibold opacity-75">· {ROL_INSTRUCTOR_LABEL[i.role] ?? i.role}</span>
                </span>
              ))}
            </div>
          )}
        </div>
        {tab === 'avisos' && !cerrada && (
          <Button variant="gradient" onClick={irANuevoAviso}>
            Nuevo aviso
          </Button>
        )}
      </div>

      {esCurso ? (
        <h2 className="font-heading text-[15px] font-semibold text-ink">Avisos</h2>
      ) : (
        <Tabs value={tab} onValueChange={cambiarTab}>
          <TabsList variant="segmented">
            <TabsTrigger value="evaluacion">Evaluación</TabsTrigger>
            <TabsTrigger value="avisos">Avisos</TabsTrigger>
          </TabsList>
        </Tabs>
      )}

      {tab === 'evaluacion' ? (
        sinConfig ? (
          <GlassCard className="p-4">
            <EmptyState
              title="Esta comisión no tiene configuración de evaluación"
              description="Las comisiones de ciclos anteriores quedan como registro, sin fechas de evaluación cargadas en el sistema."
            />
          </GlassCard>
        ) : (
          <DocenteConfiguracionEvaluacion />
        )
      ) : (
        <>
          {cerrada && (
            <div className="nota-fila flex items-start gap-2.5 py-1 pl-3 text-[12.5px] leading-relaxed text-ink-soft">
              El cuatrimestre de esta comisión ya cerró: los avisos quedan como registro y no se pueden crear, editar ni
              eliminar.
            </div>
          )}
          <TablaAvisos
            avisos={avisos.data}
            isLoading={avisos.isLoading}
            isError={avisos.isError}
            onReintentar={avisos.refetch}
            soloLectura={cerrada}
            onNuevoAviso={irANuevoAviso}
            onEditar={irAEditar}
            onEliminar={(aviso) => eliminarAviso.mutateAsync(aviso.id)}
            eliminando={eliminarAviso.isPending}
          />
        </>
      )}
    </PageContainer>
  );
}
