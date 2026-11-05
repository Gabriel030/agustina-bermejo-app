import { useNavigate, useParams } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { useCurso, useCursoPricing, useCursoTemario } from '@/features/enrollment/hooks/useCatalogo';
import { formatMoney } from '@/lib/utils';
import { courseSummary, formatWeeklyWorkload } from '@/lib/catalog';
import { CatalogBlock } from '@/features/enrollment/components/CatalogBlock';
import { CatalogItemList } from '@/features/enrollment/components/CatalogItemList';
import {
  CatalogDetailLayout,
  CatalogDetailLoadError,
  CatalogDetailLoading,
  CatalogEnrollAction,
} from '@/features/enrollment/components/CatalogDetailLayout';

const CATALOG_PATH = '/catalogo?tab=cursos';

function enrollmentReason(course) {
  if (!course.registrationOpen) return { text: 'La inscripción a este curso está cerrada.', tone: 'muted' };
  if (course.full) {
    return { text: 'El cupo está completo: si te inscribís, quedás en lista de espera.', tone: 'warning' };
  }
  return { text: null, tone: 'muted' };
}

export function DetalleCurso() {
  const { id } = useParams();
  const navigate = useNavigate();

  const courseQuery = useCurso(id);
  const pricingQuery = useCursoPricing(id);
  const syllabusQuery = useCursoTemario(id);
  const curso = courseQuery.data;

  if (courseQuery.isError) {
    return (
      <CatalogDetailLoadError
        error={courseQuery.error}
        onRetry={courseQuery.refetch}
        resource="el curso"
        backTo={CATALOG_PATH}
      />
    );
  }
  if (courseQuery.isLoading || !curso) {
    return <CatalogDetailLoading />;
  }

  const reason = enrollmentReason(curso);

  return (
    <CatalogDetailLayout
      backTo={CATALOG_PATH}
      title={curso.name}
      summary={courseSummary(curso)}
      action={
        <CatalogEnrollAction
          onEnroll={() => navigate(`/catalogo/cursos/${id}/inscribirme`)}
          disabled={!curso.registrationOpen}
          reason={reason.text}
          reasonTone={reason.tone}
        />
      }
    >
      <CatalogBlock query={pricingQuery} resource="los precios">
        <GlassCard className="flex flex-wrap gap-6 p-5">
          <div>
            <div className="text-xs text-ink-faint">Cuota mensual</div>
            <div className="text-[15px] font-bold text-ink">{formatMoney(pricingQuery.data?.cuotaMensual) || 'A definir'}</div>
          </div>
        </GlassCard>
      </CatalogBlock>

      <h2 className="font-heading text-[15px] font-semibold text-ink-soft">Temario</h2>
      <CatalogBlock query={syllabusQuery} resource="el temario" skeletonClassName="h-40">
        {syllabusQuery.data?.length === 0 && (
          <GlassCard className="p-6 text-sm text-ink-faint">Todavía no se cargó el temario de este curso.</GlassCard>
        )}
        {syllabusQuery.data?.length > 0 && (
          <CatalogItemList
            items={syllabusQuery.data.map((module) => ({
              id: module.id,
              title: module.name,
              detail: module.weeklyWorkload ? formatWeeklyWorkload(module.weeklyWorkload) : null,
            }))}
          />
        )}
      </CatalogBlock>
    </CatalogDetailLayout>
  );
}
