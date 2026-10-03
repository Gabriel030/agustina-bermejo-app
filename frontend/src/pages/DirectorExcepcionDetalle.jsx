import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { ModalMotivo } from '@/components/director/ModalMotivo';
import { UnavailableRequestState } from '@/components/director/UnavailableRequestState';
import { useExcepcion, useAprobarExcepcion, useRechazarExcepcion } from '@/hooks/useDirector';
import { formatearFecha } from '@/lib/comisiones';
import { ESTADO_TRAMITE_LABEL, TIPO_EXCEPCION_LABEL, decisionErrorMessage, isAlreadyDecided } from '@/lib/aprobaciones';

const EXCEPTIONS_TRAY = '/director/aprobaciones/excepciones';

export function DirectorExcepcionDetalle() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: excepcion, isLoading, isError, error } = useExcepcion(id);
  const aprobar = useAprobarExcepcion(id);
  const rechazar = useRechazarExcepcion(id);
  const [modalRechazo, setModalRechazo] = useState(false);

  if (isError) {
    return <UnavailableRequestState error={error} backTo={EXCEPTIONS_TRAY} backLabel="Excepciones" />;
  }

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
  const deciding = aprobar.isPending || rechazar.isPending;

  function handleDecisionError(decisionError) {
    toast.error(decisionErrorMessage(decisionError));
    if (isAlreadyDecided(decisionError)) {
      setModalRechazo(false);
    }
  }

  async function confirmarAprobar() {
    try {
      await aprobar.mutateAsync(undefined);
      toast.success('Excepción aprobada');
      navigate(EXCEPTIONS_TRAY);
    } catch (decisionError) {
      handleDecisionError(decisionError);
    }
  }

  async function confirmarRechazar(notas) {
    try {
      await rechazar.mutateAsync(notas);
      setModalRechazo(false);
      toast.success('Excepción rechazada');
      navigate(EXCEPTIONS_TRAY);
      return true;
    } catch (decisionError) {
      handleDecisionError(decisionError);
      return false;
    }
  }

  return (
    <PageContainer>
      <button
        type="button"
        onClick={() => navigate(EXCEPTIONS_TRAY)}
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
        {soloLectura && excepcion.decisionNotes && (
          <div className={`text-[12px] font-semibold ${excepcion.status === 'REJECTED' ? 'text-danger' : 'text-ink-soft'}`}>
            Notas: &ldquo;{excepcion.decisionNotes}&rdquo;
          </div>
        )}
      </GlassCard>

      {!soloLectura && (
        <div className="flex justify-end gap-2">
          <Button variant="outline" disabled={deciding} onClick={() => setModalRechazo(true)}>Rechazar</Button>
          <Button variant="gradient" loading={aprobar.isPending} disabled={deciding} onClick={confirmarAprobar}>
            Aprobar excepción
          </Button>
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
