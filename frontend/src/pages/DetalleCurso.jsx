import { useNavigate, useParams } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Button } from '@/components/ui/button';
import { useCurso, useCursoPricing, useCursoTemario } from '@/hooks/useCatalogo';
import { formatMonto } from '@/lib/utils';

export function DetalleCurso() {
  const { id } = useParams();
  const navigate = useNavigate();

  const { data: curso, isLoading, isError } = useCurso(id);
  const { data: pricing } = useCursoPricing(id);
  const { data: temario } = useCursoTemario(id);

  if (isError) {
    return <GlassCard className="p-6 text-sm text-danger">No pudimos cargar el curso. Probá recargar la página.</GlassCard>;
  }
  if (isLoading || !curso) {
    return <div className="text-sm text-ink-faint">Cargando…</div>;
  }

  return (
    <PageContainer>
      <button
        type="button"
        onClick={() => navigate('/catalogo?tab=cursos')}
        className="flex w-fit items-center gap-1.5 text-[13px] font-semibold text-ink-soft hover:text-ink"
      >
        ← Catálogo
      </button>

      <div className="aspect-video w-full rounded-xl bg-gradient-to-br from-accent/20 to-accent-2/20 flex items-center justify-center">
        <span className="text-sm font-semibold text-ink-faint">Video introductorio próximamente</span>
      </div>

      <div className="flex flex-col items-start justify-between gap-4 md:flex-row md:items-center">
        <div>
          <div className="font-heading text-2xl font-bold tracking-tight text-ink">{curso.name}</div>
          <div className="mt-1 text-sm text-ink-faint">
            {curso.durationMonths} meses{curso.schedule ? ` · ${curso.schedule}` : ''}
          </div>
        </div>
        <Button
          onClick={() => navigate(`/catalogo/cursos/${id}/inscribirme`)}
          className="w-full rounded-xl bg-gradient-to-br from-accent to-accent-2 px-6 py-5 text-[13.5px] font-semibold text-white md:w-auto"
        >
          Inscribirme
        </Button>
      </div>

      <GlassCard className="flex flex-wrap gap-6 p-5">
        <div>
          <div className="text-xs text-ink-faint">Cuota mensual</div>
          <div className="text-[15px] font-bold text-ink">{formatMonto(pricing?.cuotaMensual) || 'A definir'}</div>
        </div>
        <div>
          <div className="text-xs text-ink-faint">Duración</div>
          <div className="text-[15px] font-bold text-ink">{curso.durationMonths} cuotas mensuales</div>
        </div>
      </GlassCard>

      <div className="font-heading text-[15px] font-semibold text-ink-soft">Temario</div>
      {temario?.length === 0 && (
        <GlassCard className="p-6 text-sm text-ink-faint">Todavía no se cargó el temario de este curso.</GlassCard>
      )}
      <GlassCard className="flex flex-col p-1">
        {temario?.map((modulo) => (
          <div key={modulo.id} className="flex items-center justify-between border-b border-border px-4 py-3 last:border-b-0">
            <span className="text-[13.5px] font-semibold text-ink">{modulo.name}</span>
            {modulo.weeklyWorkload && <span className="text-xs text-ink-faint">{modulo.weeklyWorkload}hs/semana</span>}
          </div>
        ))}
      </GlassCard>
    </PageContainer>
  );
}
