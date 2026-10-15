import { useNavigate, useParams } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import {
  usePrograma,
  useProgramaPricing,
  useOpenProgramCohorts,
  useProgramaPlanDeEstudio,
} from '@/features/enrollment/hooks/useCatalogo';
import { formatMoney } from '@/lib/utils';
import { programSummary } from '@/lib/catalog';
import { CatalogDetailError } from '@/features/enrollment/components/CatalogDetailError';

// Épica 7 Fase 3. Video introductorio: placeholder visual únicamente (decisión
// confirmada con el usuario — sin carga real todavía).
export function DetalleCarrera() {
  const { id } = useParams();
  const navigate = useNavigate();

  const { data: programa, isLoading, isError, error } = usePrograma(id);
  const { data: pricing } = useProgramaPricing(id);
  const { data: cohorts } = useOpenProgramCohorts(id);
  const { data: planDeEstudio } = useProgramaPlanDeEstudio(id);

  if (isError) {
    return (
      <CatalogDetailError
        message={
          error?.status === 404
            ? 'Esta carrera no existe o ya no está disponible.'
            : 'No pudimos cargar la carrera. Probá recargar la página.'
        }
        onBack={() => navigate('/catalogo?tab=carreras')}
      />
    );
  }
  if (isLoading || !programa) {
    return (
      <PageContainer>
        <Skeleton className="h-9 w-72" />
        <Skeleton className="h-48" />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
      </PageContainer>
    );
  }

  const materiasPorAnio = (planDeEstudio ?? []).reduce((acc, materia) => {
    const anio = materia.curricularYear ?? 0;
    (acc[anio] ??= []).push(materia);
    return acc;
  }, {});

  return (
    <PageContainer>
      <button
        type="button"
        onClick={() => navigate('/catalogo?tab=carreras')}
        className="flex w-fit items-center gap-1.5 text-[13px] font-semibold text-ink-soft hover:text-ink"
      >
        ← Catálogo
      </button>

      <div className="aspect-video w-full rounded-xl bg-gradient-to-br from-brand/20 to-accent-2/20 flex items-center justify-center">
        <span className="text-sm font-semibold text-ink-faint">Video introductorio próximamente</span>
      </div>

      <div className="flex flex-col items-start justify-between gap-4 md:flex-row md:items-center">
        <div>
          <div className="font-heading text-2xl font-bold tracking-tight text-ink">{programa.name}</div>
          <div className="mt-1 text-sm text-ink-faint">{programSummary(programa)}</div>
        </div>
        <div className="flex w-full flex-col items-end gap-1.5 md:w-auto">
          <Button
            onClick={() => navigate(`/catalogo/carreras/${id}/inscribirme`)}
            disabled={!cohorts?.length}
            variant="gradient"
            className="w-full px-6 py-5 text-[13.5px] md:w-auto"
          >
            Inscribirme
          </Button>
          {cohorts && cohorts.length === 0 && (
            <span className="text-xs font-medium text-ink-faint">Todavía no hay cohortes abiertas para esta carrera.</span>
          )}
        </div>
      </div>

      <GlassCard className="flex flex-wrap gap-6 p-5">
        <div>
          <div className="text-xs text-ink-faint">Matrícula (1ª cohorte)</div>
          <div className="text-[15px] font-bold text-ink">{formatMoney(pricing?.matriculaCohorte1) || 'A definir'}</div>
        </div>
        <div>
          <div className="text-xs text-ink-faint">Matrícula (2ª cohorte)</div>
          <div className="text-[15px] font-bold text-ink">{formatMoney(pricing?.matriculaCohorte2) || 'A definir'}</div>
        </div>
        <div>
          <div className="text-xs text-ink-faint">Cuota mensual</div>
          <div className="text-[15px] font-bold text-ink">{formatMoney(pricing?.cuotaMensual) || 'A definir'}</div>
        </div>
      </GlassCard>

      <div className="font-heading text-[15px] font-semibold text-ink-soft">Plan de estudio</div>
      {planDeEstudio?.length === 0 && (
        <GlassCard className="p-6 text-sm text-ink-faint">Todavía no se cargó el plan de estudio de esta carrera.</GlassCard>
      )}
      {Object.entries(materiasPorAnio).map(([anio, materias]) => (
        <GlassCard key={anio} className="flex flex-col p-1">
          <div className="px-4 pt-3 text-[12.5px] font-bold uppercase tracking-wide text-ink-faint">Año {anio}</div>
          {materias.map((materia) => (
            <div key={materia.id} className="flex items-center justify-between border-b border-border px-4 py-3 last:border-b-0">
              <span className="text-[13.5px] font-semibold text-ink">{materia.subjectName}</span>
              <span className="text-xs text-ink-faint">{materia.credits} créditos</span>
            </div>
          ))}
        </GlassCard>
      ))}
    </PageContainer>
  );
}
