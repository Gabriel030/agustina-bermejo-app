import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { StatusPill } from '@/components/StatusPill';
import { BandejaTramites } from '@/components/BandejaTramites';
import { useEgresosPorVerificar, useHistorialVerificacionEgresos } from '@/features/graduation/hooks/useGraduation';
import { GRADUATION_VERIFICATION_TRAY, verificationStatusLabel } from '@/features/graduation/lib/graduationVerification';
import { formatearFecha } from '@/lib/comisiones';
import { ageLabel } from '@/lib/aprobaciones';

function historySubtitle(graduation) {
  const date = formatearFecha(graduation.decidedAt ?? graduation.requestedAt) ?? '—';
  if (graduation.status === 'REJECTED' && graduation.rejectionReason) {
    return `${date} · Motivo: “${graduation.rejectionReason}”`;
  }
  return date;
}

function Row({ graduation, isHistory }) {
  const pill = verificationStatusLabel(graduation.status);
  return (
    <Link
      to={`${GRADUATION_VERIFICATION_TRAY}/${graduation.id}`}
      className="flex items-center justify-between gap-4 rounded-2xl px-4 py-3 transition-colors hover:bg-neutral-bg/60"
    >
      <div className="min-w-0">
        <div className="font-heading text-size-14 font-semibold text-ink">
          {graduation.studentName} · {graduation.programName}
        </div>
        <div className="mt-0.5 text-size-12 text-ink-faint">
          {isHistory
            ? historySubtitle(graduation)
            : `Solicitado ${formatearFecha(graduation.requestedAt) ?? '—'} · ${ageLabel(graduation.requestedAt) ?? ''}`}
        </div>
      </div>
      <div className="flex items-center gap-3">
        <StatusPill tone={pill.tone}>{pill.label}</StatusPill>
        <ChevronRight className="h-4 w-4 text-ink-faint" aria-hidden="true" />
      </div>
    </Link>
  );
}

export function AdministrativoEgresosPorVerificar() {
  return (
    <PageContainer>
      <BandejaTramites
        titulo="Egresos por verificar"
        volverA={null}
        etiquetaPendientes="Por verificar"
        contadorHistorial={false}
        recurso="los egresos"
        useListaPendientes={useEgresosPorVerificar}
        useListaHistorial={useHistorialVerificacionEgresos}
        renderFila={(graduation, { esHistorial }) => (
          <Row key={graduation.id} graduation={graduation} isHistory={esHistorial} />
        )}
        extraerTextoBusqueda={(graduation) => `${graduation.studentName} ${graduation.programName}`}
        extraerFecha={(graduation, tab) => {
          const value = tab === 'historial' ? (graduation.decidedAt ?? graduation.requestedAt) : graduation.requestedAt;
          return value ? new Date(value) : null;
        }}
        tituloVacioPendientes="Nada por verificar"
        tituloVacioHistorial="Todavía no hay egresos en el historial"
      />
    </PageContainer>
  );
}
