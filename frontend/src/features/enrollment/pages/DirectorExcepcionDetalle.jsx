import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { ModalMotivo } from '@/components/ModalMotivo';
import { ModalAprobacion } from '@/components/director/ModalAprobacion';
import { UnavailableRequestState } from '@/components/director/UnavailableRequestState';
import {
  useExcepcion,
  useAprobarExcepcion,
  useRechazarExcepcion,
} from '@/features/enrollment/hooks/useExceptionApprovals';
import { formatearFecha } from '@/lib/comisiones';
import { ESTADO_TRAMITE_LABEL, TIPO_EXCEPCION_LABEL, decisionErrorMessage, isAlreadyDecided, MIN_REJECTION_REASON_LENGTH } from '@/lib/aprobaciones';

const EXCEPTIONS_TRAY = '/director/aprobaciones/excepciones';

export function DirectorExcepcionDetalle() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: excepcion, isLoading, isError, error, refetch } = useExcepcion(id);
  const aprobar = useAprobarExcepcion(id);
  const rechazar = useRechazarExcepcion(id);
  const [modalRechazo, setModalRechazo] = useState(false);
  const [modalAprobacion, setModalAprobacion] = useState(false);
  const [decisionError, setDecisionError] = useState(null);

  if (isError) {
    return <UnavailableRequestState error={error} backTo={EXCEPTIONS_TRAY} backLabel="Excepciones" onRetry={refetch} />;
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

  function handleDecisionError(failure) {
    setDecisionError(decisionErrorMessage(failure));
    toast.error(decisionErrorMessage(failure));
    if (isAlreadyDecided(failure)) {
      setModalRechazo(false);
      setModalAprobacion(false);
    }
  }

  async function confirmarAprobar(notas) {
    setDecisionError(null);
    try {
      await aprobar.mutateAsync(notas || undefined);
      setModalAprobacion(false);
      toast.success('Excepción aprobada');
      navigate(EXCEPTIONS_TRAY);
      return true;
    } catch (failure) {
      handleDecisionError(failure);
      return false;
    }
  }

  async function confirmarRechazar(notas) {
    try {
      setDecisionError(null);
      await rechazar.mutateAsync(notas);
      setModalRechazo(false);
      toast.success('Excepción rechazada');
      navigate(EXCEPTIONS_TRAY);
      return true;
    } catch (failure) {
      handleDecisionError(failure);
      return false;
    }
  }

  return (
    <PageContainer>
      <Link
        to={EXCEPTIONS_TRAY}
        className="flex w-fit items-center gap-1.5 text-[13px] font-semibold text-ink-soft hover:text-ink"
      >
        ← Excepciones
      </Link>

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
        <div className="flex flex-col items-end gap-2">
          {decisionError && <div role="alert" className="text-[13px] font-semibold text-danger">{decisionError}</div>}
          <div className="flex justify-end gap-2">
            <Button variant="outline" disabled={deciding} onClick={() => setModalRechazo(true)}>Rechazar</Button>
            <Button variant="gradient" disabled={deciding} onClick={() => setModalAprobacion(true)}>
              Aprobar excepción
            </Button>
          </div>
        </div>
      )}

      <ModalAprobacion
        open={modalAprobacion}
        onClose={() => setModalAprobacion(false)}
        confirmando={aprobar.isPending}
        titulo="Aprobar excepción"
        withNote
        onConfirmar={confirmarAprobar}
        resumen={
          <>
            <div className="font-semibold">{TIPO_EXCEPCION_LABEL[excepcion.type] ?? excepcion.type}</div>
            <div>{excepcion.studentName} · {excepcion.programName}{excepcion.subjectName ? ` · ${excepcion.subjectName}` : ''}</div>
          </>
        }
      />

      <ModalMotivo
        open={modalRechazo}
        onClose={() => setModalRechazo(false)}
        confirmando={rechazar.isPending}
        requerido
        minLength={MIN_REJECTION_REASON_LENGTH}
        titulo="Rechazar excepción"
        onConfirmar={confirmarRechazar}
      />
    </PageContainer>
  );
}
