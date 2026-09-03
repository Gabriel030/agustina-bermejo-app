import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { ModalMotivo } from '@/components/director/ModalMotivo';
import { useExcepcion, useAprobarExcepcion, useRechazarExcepcion } from '@/hooks/useDirector';
import { formatearFecha } from '@/lib/comisiones';
import { ESTADO_TRAMITE_LABEL, TIPO_EXCEPCION_LABEL } from '@/lib/aprobaciones';

export function DirectorExcepcionDetalle() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: excepcion, isLoading } = useExcepcion(id);
  const aprobar = useAprobarExcepcion(id);
  const rechazar = useRechazarExcepcion(id);
  const [modalRechazo, setModalRechazo] = useState(false);

  if (isLoading || !excepcion) {
    return (
      <PageContainer>
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-40" />
      </PageContainer>
    );
  }

  const soloLectura = excepcion.status === 'APPROVED' || excepcion.status === 'REJECTED';
  const pill = ESTADO_TRAMITE_LABEL[excepcion.status] ?? { label: excepcion.status, tone: 'warning' };

  async function confirmarAprobar() {
    await aprobar.mutateAsync(undefined);
    navigate('/director/aprobaciones/excepciones');
  }

  async function confirmarRechazar(notas) {
    await rechazar.mutateAsync(notas);
    setModalRechazo(false);
    navigate('/director/aprobaciones/excepciones');
  }

  return (
    <PageContainer>
      <button
        type="button"
        onClick={() => navigate('/director/aprobaciones/excepciones')}
        className="flex w-fit items-center gap-1.5 text-[13px] font-semibold text-ink-soft hover:text-ink"
      >
        ← Excepciones
      </button>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl font-bold text-ink">{TIPO_EXCEPCION_LABEL[excepcion.type] ?? excepcion.type}</h1>
          <p className="mt-0.5 text-sm text-ink-soft">
            {excepcion.studentName} · {excepcion.programName}{excepcion.subjectName ? ` · ${excepcion.subjectName}` : ''}
          </p>
        </div>
        {soloLectura && <StatusPill tone={pill.tone}>{pill.label}</StatusPill>}
      </div>

      <GlassCard className="flex flex-col gap-3 p-4">
        <div>
          <div className="text-xs font-semibold text-ink-soft">Justificación</div>
          <div className="mt-1 text-[13.5px] text-ink">&ldquo;{excepcion.justification}&rdquo;</div>
        </div>
        <div className="text-[12px] text-ink-faint">Solicitado {formatearFecha(excepcion.requestedAt) ?? '—'}</div>
        {soloLectura && (
          <div className="text-[12px] text-ink-faint">Decidido {formatearFecha(excepcion.decidedAt) ?? '—'}</div>
        )}
        {excepcion.status === 'REJECTED' && excepcion.decisionNotes && (
          <div className="text-[12px] font-semibold text-danger">Notas: &ldquo;{excepcion.decisionNotes}&rdquo;</div>
        )}
      </GlassCard>

      {!soloLectura && (
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={() => setModalRechazo(true)}>Rechazar</Button>
          <Button variant="gradient" disabled={aprobar.isPending} onClick={confirmarAprobar}>Aprobar excepción</Button>
        </div>
      )}

      <ModalMotivo
        open={modalRechazo}
        onClose={() => setModalRechazo(false)}
        confirmando={rechazar.isPending}
        requerido={false}
        onConfirmar={confirmarRechazar}
      />
    </PageContainer>
  );
}
