import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { StatusPill } from '@/components/StatusPill';
import { BandejaTramites } from '@/components/BandejaTramites';
import {
  useExcepcionesPendientes,
  useHistorialExcepciones,
} from '@/features/enrollment/hooks/useExceptionApprovals';
import { formatearFecha } from '@/lib/comisiones';
import { ageLabel, ESTADO_TRAMITE_LABEL, TIPO_EXCEPCION_LABEL } from '@/lib/aprobaciones';

function Fila({ e, esHistorial }) {
  const pill = ESTADO_TRAMITE_LABEL[e.status] ?? { label: e.status, tone: 'neutral' };
  return (
    <Link
      to={`/director/aprobaciones/excepciones/${e.id}`}
      className="flex items-center justify-between gap-4 rounded-2xl px-4 py-3 transition-colors hover:bg-neutral-bg/60"
    >
      <div>
        <div className="font-heading text-size-14 font-semibold text-ink">
          {TIPO_EXCEPCION_LABEL[e.type] ?? e.type} · {e.studentName} · {e.programName}{e.subjectName ? ` · ${e.subjectName}` : ''}
        </div>
        <div className="mt-0.5 text-size-12 text-ink-faint">
          {esHistorial ? `Decidido ${formatearFecha(e.decidedAt) ?? '—'}` : `Solicitado ${formatearFecha(e.requestedAt) ?? '—'} · ${ageLabel(e.requestedAt) ?? ''}`}
        </div>
      </div>
      <div className="flex items-center gap-3">
        <StatusPill tone={pill.tone}>{pill.label}</StatusPill>
        <ChevronRight className="h-4 w-4 text-ink-faint" />
      </div>
    </Link>
  );
}

export function DirectorAprobacionesExcepciones() {
  return (
    <PageContainer>
      <BandejaTramites
        titulo="Excepciones"
        useListaPendientes={useExcepcionesPendientes}
        useListaHistorial={useHistorialExcepciones}
        renderFila={(e, { esHistorial }) => <Fila key={e.id} e={e} esHistorial={esHistorial} />}
        extraerTextoBusqueda={(e) => `${e.studentName} ${e.programName} ${e.subjectName ?? ''}`}
        extraerFecha={(e, tab) => {
          const valor = tab === 'historial' ? e.decidedAt : e.requestedAt;
          return valor ? new Date(valor) : null;
        }}
        tituloVacioPendientes="Ninguna excepción pendiente"
        tituloVacioHistorial="Todavía no decidiste ninguna excepción"
      />
    </PageContainer>
  );
}
