import { useNavigate } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatCard } from '@/components/StatCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { formatearFecha } from '@/lib/comisiones';
import {
  useConfigsPendientes,
  useEgresosPendientes,
  useExcepcionesPendientes,
} from '@/hooks/useDirector';
import { TIPO_EXCEPCION_LABEL } from '@/lib/aprobaciones';

const PREVIEW = 3;

function SeccionPreview({ titulo, ruta, cargando, items, tituloVacio, renderItem }) {
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
          {total === 0 ? 'Ver todo' : `Ver los ${total} pendientes`}
          <ChevronRight className="h-4 w-4" />
        </div>
      </div>
      {cargando ? (
        <div className="flex flex-col gap-2 p-2">
          <Skeleton className="h-12" />
        </div>
      ) : total === 0 ? (
        <EmptyState title={tituloVacio} className="py-6" />
      ) : (
        items.slice(0, PREVIEW).map(renderItem)
      )}
    </GlassCard>
  );
}

export function DirectorAprobaciones() {
  const { data: pendientes, isLoading: cargandoPendientes } = useConfigsPendientes();
  const { data: egresosPendientes, isLoading: cargandoEgresos } = useEgresosPendientes();
  const { data: excepcionesPendientes, isLoading: cargandoExcepciones } = useExcepcionesPendientes();

  const listaPendientes = pendientes ?? [];
  const listaEgresosPendientes = egresosPendientes ?? [];
  const listaExcepcionesPendientes = excepcionesPendientes ?? [];

  return (
    <PageContainer>
      <div>
        <h1 className="font-heading text-2xl font-bold text-ink">Aprobaciones</h1>
        <p className="mt-0.5 text-sm text-ink-soft">Trámites esperando tu decisión, y lo que ya decidiste</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard label="Egresos por aprobar">
          <div className="font-heading text-2xl font-bold text-ink">{listaEgresosPendientes.length}</div>
        </StatCard>
        <StatCard label="Excepciones por decidir">
          <div className="font-heading text-2xl font-bold text-ink">{listaExcepcionesPendientes.length}</div>
        </StatCard>
        <StatCard label="Config. de evaluación pendientes">
          <div className="font-heading text-2xl font-bold text-ink">{listaPendientes.length}</div>
        </StatCard>
      </div>

      <SeccionPreview
        titulo="Configuración de evaluación pendiente"
        ruta="/director/aprobaciones/configuracion-evaluacion"
        cargando={cargandoPendientes}
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
        cargando={cargandoEgresos}
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
        cargando={cargandoExcepciones}
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
