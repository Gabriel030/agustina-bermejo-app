import { useNavigate, useParams } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import {
  usePrograma,
  useProgramaPricing,
  useOpenProgramCohorts,
  useProgramaPlanDeEstudio,
} from '@/features/enrollment/hooks/useCatalogo';
import { formatMoney } from '@/lib/utils';
import { formatCredits, groupSubjectsByYear, isListedInCatalog, programSummary, subjectTag } from '@/lib/catalog';
import { useProgramViewerStatus } from '@/features/enrollment/hooks/useCatalogViewer';
import { CatalogBlock } from '@/features/enrollment/components/CatalogBlock';
import { CatalogItemList } from '@/features/enrollment/components/CatalogItemList';
import {
  CatalogDetailLayout,
  CatalogDetailLoadError,
  CatalogDetailLoading,
  CatalogEnrollAction,
  CatalogViewerAction,
} from '@/features/enrollment/components/CatalogDetailLayout';

const CATALOG_PATH = '/catalogo?tab=carreras';

function enrollmentReason(cohortsQuery) {
  if (cohortsQuery.isError) return 'No pudimos verificar las cohortes abiertas.';
  if (cohortsQuery.data && cohortsQuery.data.length === 0) return 'Todavía no hay cohortes abiertas para esta carrera.';
  return null;
}

export function DetalleCarrera() {
  const { id } = useParams();
  const navigate = useNavigate();

  const programQuery = usePrograma(id);
  const pricingQuery = useProgramaPricing(id);
  const cohortsQuery = useOpenProgramCohorts(id);
  const planQuery = useProgramaPlanDeEstudio(id);
  const viewer = useProgramViewerStatus(id);
  const programa = programQuery.data;

  if (programQuery.isError) {
    return (
      <CatalogDetailLoadError
        error={programQuery.error}
        onRetry={programQuery.refetch}
        resource="la carrera"
        backTo={CATALOG_PATH}
      />
    );
  }
  if (programQuery.isLoading || !programa || viewer.loading) {
    return <CatalogDetailLoading />;
  }
  if (viewer.canApply && !isListedInCatalog(programa)) {
    return (
      <CatalogDetailLoadError
        error={{ status: 404 }}
        resource="la carrera"
        backTo={CATALOG_PATH}
      />
    );
  }

  const pricing = pricingQuery.data;

  return (
    <CatalogDetailLayout
      backTo={CATALOG_PATH}
      hiddenNotice={viewer.state === 'PREVIEW' && !isListedInCatalog(programa) ? 'Programa no visible en el catálogo' : null}
      title={programa.name}
      summary={programSummary(programa)}
      action={
        <CatalogViewerAction
          viewer={viewer}
          previewText="Vista previa del catálogo"
          notices={{
            ENROLLED: { text: 'Ya cursás esta carrera.', to: '/alumno/mis-carreras', linkLabel: 'Ir a Mis carreras' },
            PENDING_APPLICATION: {
              text: 'Ya tenés una solicitud pendiente para esta carrera.',
              to: `/mis-solicitudes/${viewer.applicationId}`,
              linkLabel: 'Ver solicitud',
            },
          }}
          enrollAction={
            <CatalogEnrollAction
              onEnroll={() => navigate(`/catalogo/carreras/${id}/inscribirme`)}
              disabled={!cohortsQuery.data?.length}
              reason={enrollmentReason(cohortsQuery)}
              onRetry={cohortsQuery.isError ? cohortsQuery.refetch : undefined}
            />
          }
        />
      }
    >
      <CatalogBlock query={pricingQuery} resource="los precios">
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
      </CatalogBlock>

      <h2 className="font-heading text-[15px] font-semibold text-ink-soft">Plan de estudio</h2>
      <CatalogBlock query={planQuery} resource="el plan de estudio" skeletonClassName="h-40">
        {planQuery.data?.length === 0 && (
          <GlassCard className="p-6 text-sm text-ink-faint">Todavía no se cargó el plan de estudio de esta carrera.</GlassCard>
        )}
        {groupSubjectsByYear(planQuery.data).map((group) => (
          <CatalogItemList
            key={group.year}
            heading={group.label}
            items={group.items.map((subject) => ({
              id: subject.id,
              title: subject.subjectName,
              detail: formatCredits(subject.credits),
              tag: subjectTag(subject),
            }))}
          />
        ))}
      </CatalogBlock>
    </CatalogDetailLayout>
  );
}
