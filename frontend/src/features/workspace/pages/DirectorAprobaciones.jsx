import { useNavigate } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatCard } from '@/components/StatCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { formatearFecha } from '@/lib/comisiones';
import { useConfigsPendientes } from '@/features/evaluation';
import { useEgresosPendientes } from '@/features/graduation';
import { useExcepcionesPendientes } from '@/features/enrollment';
import { TIPO_EXCEPCION_LABEL } from '@/lib/aprobaciones';

const PREVIEW = 3;

function pendingLinkLabel(total, failed) {
  if (failed || total === 0) {
    return 'Ver todo';
  }
  return total === 1 ? 'Ver el pendiente' : `Ver los ${total} pendientes`;
}

function PendingCounter({ query }) {
  if (query.isLoading) {
    return <Skeleton className="h-8 w-12" />;
  }
  return <div className="font-heading text-2xl font-bold text-ink">{query.isError ? '—' : (query.data ?? []).length}</div>;
}

function SeccionPreview({ titulo, ruta, cargando, fallo, items, tituloVacio, renderItem }) {
  const navigate = useNavigate();
  const total = items.length;

  return (
    <GlassCard
      onClick={() => navigate(ruta)}
      className="flex cursor-pointer flex-col gap-1 p-4 transition-colors hover:bg-neutral-bg/40"
    >
      <div className="flex items-center justify-between px-2 pb-2">
        <div className="font-heading text-[14.5px] font-semibold text-ink">{titulo}</div>
        <div className="flex items-center gap-1 text-[12.5px] font-semibold text-primary">
          {pendingLinkLabel(total, fallo)}
          <ChevronRight className="h-4 w-4" />
        </div>
      </div>
      {cargando ? (
        <div className="flex flex-col gap-2 p-2">
          <Skeleton className="h-12" />
        </div>
      ) : fallo ? (
        <EmptyState title="No pudimos cargar esta bandeja" description="Probá recargar la página en un momento." className="py-6" />
      ) : total === 0 ? (
        <EmptyState title={tituloVacio} className="py-6" />
      ) : (
        items.slice(0, PREVIEW).map(renderItem)
      )}
    </GlassCard>
  );
}

export function DirectorAprobaciones() {
  const configsQuery = useConfigsPendientes();
  const graduationsQuery = useEgresosPendientes();
  const exceptionsQuery = useExcepcionesPendientes();

  const listaPendientes = configsQuery.data ?? [];
  const listaEgresosPendientes = graduationsQuery.data ?? [];
  const listaExcepcionesPendientes = exceptionsQuery.data ?? [];

  return (
    <PageContainer>
      <div>
        <h1 className="font-heading text-2xl font-bold text-ink">Aprobaciones</h1>
        <p className="mt-0.5 text-sm text-ink-soft">Trámites esperando tu decisión, y lo que ya decidiste</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard label="Egresos por aprobar">
          <PendingCounter query={graduationsQuery} />
        </StatCard>
        <StatCard label="Excepciones por decidir">
          <PendingCounter query={exceptionsQuery} />
        </StatCard>
        <StatCard label="Config. de evaluación pendientes">
          <PendingCounter query={configsQuery} />
        </StatCard>
      </div>

      <SeccionPreview
        titulo="Configuración de evaluación pendiente"
        ruta="/director/aprobaciones/configuracion-evaluacion"
        cargando={configsQuery.isLoading}
        fallo={configsQuery.isError}
        items={listaPendientes}
        tituloVacio="Nada pendiente"
        renderItem={(c) => (
          <div key={c.id} className="flex items-center justify-between gap-4 rounded-2xl px-4 py-2.5">
            <div className="text-[13.5px] font-semibold text-ink">{c.subjectName} · {c.sectionName}</div>
            <div className="text-[12px] text-ink-faint">enviada {formatearFecha(c.submittedAt) ?? '—'}</div>
          </div>
        )}
      />

      <SeccionPreview
        titulo="Egresos por aprobar"
        ruta="/director/aprobaciones/egresos"
        cargando={graduationsQuery.isLoading}
        fallo={graduationsQuery.isError}
        items={listaEgresosPendientes}
        tituloVacio="Ningún egreso esperando tu decisión"
        renderItem={(g) => (
          <div key={g.id} className="flex items-center justify-between gap-4 rounded-2xl px-4 py-2.5">
            <div className="text-[13.5px] font-semibold text-ink">{g.studentName} · {g.programName}</div>
            <div className="text-[12px] text-ink-faint">solicitado {formatearFecha(g.requestedAt) ?? '—'}</div>
          </div>
        )}
      />

      <SeccionPreview
        titulo="Excepciones por decidir"
        ruta="/director/aprobaciones/excepciones"
        cargando={exceptionsQuery.isLoading}
        fallo={exceptionsQuery.isError}
        items={listaExcepcionesPendientes}
        tituloVacio="Ninguna excepción pendiente"
        renderItem={(e) => (
          <div key={e.id} className="flex items-center justify-between gap-4 rounded-2xl px-4 py-2.5">
            <div className="text-[13.5px] font-semibold text-ink">
              {TIPO_EXCEPCION_LABEL[e.type] ?? e.type} · {e.studentName}
            </div>
            <div className="text-[12px] text-ink-faint">solicitado {formatearFecha(e.requestedAt) ?? '—'}</div>
          </div>
        )}
      />
    </PageContainer>
  );
}
