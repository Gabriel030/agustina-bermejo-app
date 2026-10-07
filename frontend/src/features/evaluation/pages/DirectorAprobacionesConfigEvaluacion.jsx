import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { StatusPill } from '@/components/StatusPill';
import { BandejaTramites } from '@/components/director/BandejaTramites';
import {
  useConfigsPendientes,
  useHistorialAprobaciones,
} from '@/features/evaluation/hooks/useEvaluationConfig';
import { ESTADO_CONFIG_LABEL, formatearFecha } from '@/lib/comisiones';

function Fila({ c, esHistorial }) {
  const pill = esHistorial ? (ESTADO_CONFIG_LABEL[c.status] ?? { label: c.status, tone: 'neutral' }) : null;
  return (
    <Link
      key={c.id}
      to={`/director/revisar-configuracion/${c.subjectSectionId}`}
      className="flex items-center justify-between gap-4 rounded-2xl px-4 py-3 transition-colors hover:bg-neutral-bg/60"
    >
      <div>
        <div className="font-heading text-[14px] font-semibold text-ink">{c.subjectName} · {c.sectionName}</div>
        {esHistorial ? (
          <>
            <div className="mt-0.5 text-[12px] text-ink-faint">
              {c.reviewedByName ? `Decidido por ${c.reviewedByName}` : 'Sin registro de quién decidió'} · {formatearFecha(c.reviewedAt) ?? '—'}
            </div>
            {c.status === 'REJECTED' && c.rejectionReason && (
              <div className="mt-0.5 text-[12px] font-semibold text-danger">Motivo: &ldquo;{c.rejectionReason}&rdquo;</div>
            )}
          </>
        ) : (
          <div className="mt-0.5 text-[12px] text-ink-faint">
            {c.instructors?.find((i) => i.role === 'LEAD')?.name ?? 'Sin titular'} (Titular) · enviada {formatearFecha(c.submittedAt) ?? '—'}
          </div>
        )}
      </div>
      <div className="flex items-center gap-3">
        {esHistorial ? (
          <StatusPill tone={pill.tone}>{pill.label}</StatusPill>
        ) : (
          <span className="rounded-full bg-warning-bg px-2.5 py-1 text-[11px] font-bold text-warning">Pendiente de aprobación</span>
        )}
        <ChevronRight className="h-4 w-4 text-ink-faint" />
      </div>
    </Link>
  );
}

export function DirectorAprobacionesConfigEvaluacion() {
  return (
    <PageContainer>
      <BandejaTramites
        titulo="Configuración de evaluación"
        useListaPendientes={useConfigsPendientes}
        useListaHistorial={useHistorialAprobaciones}
        renderFila={(c, { esHistorial }) => <Fila key={c.id} c={c} esHistorial={esHistorial} />}
        extraerTextoBusqueda={(c) => `${c.subjectName} ${c.sectionName}`}
        extraerFecha={(c, tab) => {
          const valor = tab === 'historial' ? c.reviewedAt : c.submittedAt;
          return valor ? new Date(valor) : null;
        }}
        tituloVacioPendientes="Nada pendiente"
        tituloVacioHistorial="Todavía no decidiste nada"
      />
    </PageContainer>
  );
}
