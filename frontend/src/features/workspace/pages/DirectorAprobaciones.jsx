import { Link } from 'react-router-dom';
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
import { useAuthorizationRequests } from '@/features/billing';
import { usePendingOfferCycles } from '@/features/academic-structure';
import { ageLabel } from '@/lib/aprobaciones';
import { TIPO_EXCEPCION_LABEL } from '@/lib/aprobaciones';

const PREVIEW = 3;
const BILLING_PENDING_SEARCH = { statuses: ['PENDING'], sort: 'EXPIRES_ON', descending: false, page: 0, size: 50 };

function pendingLinkLabel(total, failed) {
  if (failed || total === 0) {
    return 'Ver todo';
  }
  return total === 1 ? 'Ver el pendiente' : `Ver los ${total} pendientes`;
}

function PendingCounter({ query, count }) {
  if (query.isLoading) {
    return <Skeleton className="h-8 w-12" />;
  }
  return <div className="font-heading text-2xl font-bold text-ink">{query.isError ? '—' : (count ?? (query.data ?? []).length)}</div>;
}

function PreviewRow({ to, title, dateLabel, date }) {
  return (
    <Link
      to={to}
      className="flex items-center justify-between gap-4 rounded-2xl px-4 py-2.5 transition-colors hover:bg-neutral-bg/60"
    >
      <div className="text-size-13-5 font-semibold text-ink">{title}</div>
      <div className="text-size-12 text-ink-faint">{dateLabel} {formatearFecha(date) ?? '—'} · {ageLabel(date)}</div>
    </Link>
  );
}

function SeccionPreview({ titulo, ruta, cargando, fallo, items, tituloVacio, renderItem }) {
  const total = items.length;

  return (
    <GlassCard className="flex flex-col gap-1 p-4">
      <Link
        to={ruta}
        className="flex items-center justify-between rounded-xl px-2 pb-2 transition-colors hover:bg-neutral-bg/40"
      >
        <div className="font-heading text-size-14-5 font-semibold text-ink">{titulo}</div>
        <div className="flex items-center gap-1 text-size-12-5 font-semibold text-primary">
          {pendingLinkLabel(total, fallo)}
          <ChevronRight className="h-4 w-4" />
        </div>
      </Link>
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
  const billingQuery = useAuthorizationRequests(BILLING_PENDING_SEARCH);
  const offerQuery = usePendingOfferCycles();

  const listaPendientes = configsQuery.data ?? [];
  const listaEgresosPendientes = graduationsQuery.data ?? [];
  const listaExcepcionesPendientes = exceptionsQuery.data ?? [];
  const listaBillingPendientes = billingQuery.data?.items ?? [];

  return (
    <PageContainer>
      <div>
        <h1 className="font-heading text-2xl font-bold text-ink">Aprobaciones</h1>
        <p className="mt-0.5 text-sm text-ink-soft">Trámites esperando tu decisión</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Link to="/director/aprobaciones/egresos" className="block rounded-[inherit]">
          <StatCard label="Egresos por aprobar">
            <PendingCounter query={graduationsQuery} />
          </StatCard>
        </Link>
        <Link to="/director/aprobaciones/excepciones" className="block rounded-[inherit]">
          <StatCard label="Excepciones por decidir">
            <PendingCounter query={exceptionsQuery} />
          </StatCard>
        </Link>
        <Link to="/director/aprobaciones/configuracion-evaluacion" className="block rounded-[inherit]">
          <StatCard label="Config. de evaluación pendientes">
            <PendingCounter query={configsQuery} />
          </StatCard>
        </Link>
        <Link to="/director/aprobaciones/oferta-academica" className="block rounded-[inherit]">
          <StatCard label="Oferta por aprobar">
            <PendingCounter query={offerQuery} />
          </StatCard>
        </Link>
        <Link to="/director/aprobaciones/facturacion" className="block rounded-[inherit]">
          <StatCard label="Operaciones de facturación">
            <PendingCounter query={billingQuery} count={billingQuery.data?.totalElements} />
          </StatCard>
        </Link>
      </div>

      <SeccionPreview
        titulo="Operaciones de facturación"
        ruta="/director/aprobaciones/facturacion"
        cargando={billingQuery.isLoading}
        fallo={billingQuery.isError}
        items={listaBillingPendientes}
        tituloVacio="Nada de facturación esperando tu decisión"
        renderItem={(r) => (
          <Link
            key={r.id}
            to="/director/aprobaciones/facturacion"
            className="flex items-center justify-between gap-4 rounded-2xl px-4 py-2.5 transition-colors hover:bg-neutral-bg/60"
          >
            <div className="text-size-13-5 font-semibold text-ink">
              {r.operationLabel} · {r.studentName ?? r.summary}
            </div>
            <div className="text-size-12 text-ink-faint">pedido por {r.requestedBy?.name ?? '—'}</div>
          </Link>
        )}
      />

      <SeccionPreview
        titulo="Configuración de evaluación pendiente"
        ruta="/director/aprobaciones/configuracion-evaluacion"
        cargando={configsQuery.isLoading}
        fallo={configsQuery.isError}
        items={listaPendientes}
        tituloVacio="Nada pendiente"
        renderItem={(c) => (
          <PreviewRow
            key={c.id}
            to={`/director/revisar-configuracion/${c.subjectSectionId}`}
            title={`${c.subjectName} · ${c.sectionName}`}
            dateLabel="enviada"
            date={c.submittedAt}
          />
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
          <PreviewRow
            key={g.id}
            to={`/director/aprobaciones/egresos/${g.id}`}
            title={`${g.studentName} · ${g.programName}`}
            dateLabel="solicitado"
            date={g.requestedAt}
          />
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
          <PreviewRow
            key={e.id}
            to={`/director/aprobaciones/excepciones/${e.id}`}
            title={`${TIPO_EXCEPCION_LABEL[e.type] ?? e.type} · ${e.studentName}`}
            dateLabel="solicitado"
            date={e.requestedAt}
          />
        )}
      />
    </PageContainer>
  );
}
