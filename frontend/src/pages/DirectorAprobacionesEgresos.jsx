import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { StatusPill } from '@/components/StatusPill';
import { BandejaTramites } from '@/components/director/BandejaTramites';
import { useEgresosPendientes, useHistorialEgresos } from '@/hooks/useDirector';
import { formatearFecha } from '@/lib/comisiones';
import { ESTADO_TRAMITE_LABEL } from '@/lib/aprobaciones';

function Fila({ g, esHistorial }) {
  const pill = ESTADO_TRAMITE_LABEL[g.status] ?? { label: g.status, tone: 'neutral' };
  return (
    <Link
      to={`/director/aprobaciones/egresos/${g.id}`}
      className="flex items-center justify-between gap-4 rounded-2xl px-4 py-3 transition-colors hover:bg-neutral-bg/60"
    >
      <div>
        <div className="font-heading text-[14px] font-semibold text-ink">{g.studentName} · {g.programName}</div>
        <div className="mt-0.5 text-[12px] text-ink-faint">
          {esHistorial ? `Decidido ${formatearFecha(g.decidedAt) ?? '—'}` : `Solicitado ${formatearFecha(g.requestedAt) ?? '—'}`}
        </div>
        {g.status === 'REJECTED' && g.rejectionReason && (
          <div className="mt-0.5 text-[12px] font-semibold text-danger">Motivo: &ldquo;{g.rejectionReason}&rdquo;</div>
        )}
      </div>
      <div className="flex items-center gap-3">
        {esHistorial ? (
          <StatusPill tone={pill.tone}>{pill.label}</StatusPill>
        ) : (
          <StatusPill tone="warning">Pendiente de revisión</StatusPill>
        )}
        <ChevronRight className="h-4 w-4 text-ink-faint" />
      </div>
    </Link>
  );
}

export function DirectorAprobacionesEgresos() {
  return (
    <PageContainer>
      <BandejaTramites
        titulo="Egresos"
        useListaPendientes={useEgresosPendientes}
        useListaHistorial={useHistorialEgresos}
        renderFila={(g, { esHistorial }) => <Fila key={g.id} g={g} esHistorial={esHistorial} />}
        extraerTextoBusqueda={(g) => `${g.studentName} ${g.programName}`}
        extraerFecha={(g, tab) => {
          const valor = tab === 'historial' ? g.decidedAt : g.requestedAt;
          return valor ? new Date(valor) : null;
        }}
        tituloVacioPendientes="Ningún egreso esperando tu decisión"
        tituloVacioHistorial="Todavía no decidiste ningún egreso"
      />
    </PageContainer>
  );
}
