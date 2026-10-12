import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatCard } from '@/components/StatCard';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { ModalMotivo } from '@/components/director/ModalMotivo';
import { UnavailableRequestState } from '@/components/director/UnavailableRequestState';
import { useEgreso, useAprobarEgreso, useRechazarEgreso } from '@/features/graduation/hooks/useGraduation';
import { useProgresoStaff } from '@/features/enrollment';
import { useAsistenciaPorMateria } from '@/features/attendance';
import { useEstadoDePago } from '@/features/billing';
import { formatearFecha } from '@/lib/comisiones';
import { ESTADO_TRAMITE_LABEL, decisionErrorMessage, isAlreadyDecided } from '@/lib/aprobaciones';

const ESTADO_PAGO_LABEL = {
  AL_DIA: { label: 'Al día', tone: 'success' },
  MOROSO: { label: 'Moroso', tone: 'danger' },
  SIN_INFORMACION: { label: 'Sin información', tone: 'neutral' },
};

const GRADUATIONS_TRAY = '/director/aprobaciones/egresos';

export function DirectorEgresoDetalle() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: egreso, isLoading: cargandoEgreso, isError, error } = useEgreso(id);
  const { data: progreso, isLoading: cargandoProgreso } = useProgresoStaff(egreso?.programEnrollmentId);
  const { data: asistencia, isLoading: cargandoAsistencia } = useAsistenciaPorMateria(egreso?.programEnrollmentId);
  const { data: pago } = useEstadoDePago(egreso?.studentId);
  const aprobar = useAprobarEgreso(id);
  const rechazar = useRechazarEgreso(id);
  const [modalRechazo, setModalRechazo] = useState(false);

  if (isError) {
    return <UnavailableRequestState error={error} backTo={GRADUATIONS_TRAY} backLabel="Egresos" />;
  }

  if (cargandoEgreso || !egreso) {
    return (
      <PageContainer>
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-40" />
      </PageContainer>
    );
  }

  const soloLectura = egreso.status === 'APPROVED' || egreso.status === 'REJECTED';
  const pillEstadoTramite = ESTADO_TRAMITE_LABEL[egreso.status] ?? { label: egreso.status, tone: 'warning' };
  const pillPago = pago ? (ESTADO_PAGO_LABEL[pago.standing] ?? { label: pago.standing, tone: 'neutral' }) : null;
  const missingMandatorySubjects = progreso?.obligatoriasPendientes ?? [];
  const meetsRequirements = progreso?.elegibleParaCompletar !== false;
  const deciding = aprobar.isPending || rechazar.isPending;

  function handleDecisionError(decisionError) {
    toast.error(decisionErrorMessage(decisionError));
    if (isAlreadyDecided(decisionError)) {
      setModalRechazo(false);
    }
  }

  async function confirmarAprobar() {
    try {
      await aprobar.mutateAsync();
      toast.success('Egreso aprobado');
      navigate(GRADUATIONS_TRAY);
    } catch (decisionError) {
      handleDecisionError(decisionError);
    }
  }

  async function confirmarRechazar(motivo) {
    try {
      await rechazar.mutateAsync(motivo);
      setModalRechazo(false);
      toast.success('Egreso rechazado');
      navigate(GRADUATIONS_TRAY);
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
        onClick={() => navigate(GRADUATIONS_TRAY)}
        className="flex w-fit items-center gap-1.5 text-[13px] font-semibold text-ink-soft hover:text-ink"
      >
        ← Egresos
      </button>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl font-bold text-ink">{egreso.studentName}</h1>
          <p className="mt-0.5 text-sm text-ink-soft">{egreso.programName} · Solicitado {formatearFecha(egreso.requestedAt) ?? '—'}</p>
        </div>
        {soloLectura && <StatusPill tone={pillEstadoTramite.tone}>{pillEstadoTramite.label}</StatusPill>}
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-4">
        <StatCard label="Créditos">
          <div className="font-heading text-2xl font-bold text-ink">
            {cargandoProgreso ? '—' : `${progreso?.creditosAcumulados ?? 0} / ${progreso?.creditosRequeridos ?? 0}`}
          </div>
        </StatCard>
        <StatCard label="Promedio general">
          <div className="font-heading text-2xl font-bold text-ink">
            {cargandoProgreso || progreso?.promedioGeneral == null ? '—' : progreso.promedioGeneral.toFixed(2)}
          </div>
        </StatCard>
        <StatCard label="Materias obligatorias pendientes">
          <div className="font-heading text-2xl font-bold text-ink">
            {cargandoProgreso ? '—' : missingMandatorySubjects.length}
          </div>
        </StatCard>
        <StatCard label="Estado de facturación">
          <div className="mt-1">
            {pillPago ? <StatusPill tone={pillPago.tone}>{pillPago.label}</StatusPill> : <span className="text-sm text-ink-faint">—</span>}
          </div>
        </StatCard>
      </div>

      {missingMandatorySubjects.length > 0 && (
        <GlassCard className="flex flex-col gap-2 p-4">
          <div className="font-heading text-[16px] font-semibold text-ink">Obligatorias que faltan</div>
          <ul className="flex flex-col gap-1">
            {missingMandatorySubjects.map((subjectName) => (
              <li key={subjectName} className="rounded-xl px-3 py-1.5 text-[13.5px] text-ink">{subjectName}</li>
            ))}
          </ul>
        </GlassCard>
      )}

      <GlassCard className="flex flex-col gap-3 p-4">
        <div className="font-heading text-[16px] font-semibold text-ink">Asistencia por materia</div>
        {cargandoAsistencia ? (
          <Skeleton className="h-24" />
        ) : (asistencia ?? []).length === 0 ? (
          <div className="text-sm text-ink-faint">Sin cursadas registradas.</div>
        ) : (
          <div className="flex flex-col gap-1">
            {asistencia.map((a) => (
              <div key={a.subjectName} className="flex items-center justify-between rounded-xl px-3 py-2 hover:bg-neutral-bg/40">
                <div className="text-[13.5px] text-ink">{a.subjectName}</div>
                <div className="text-[13.5px] font-semibold text-ink">
                  {a.attendancePercent == null ? '—' : `${Math.round(a.attendancePercent)}%`}
                </div>
              </div>
            ))}
          </div>
        )}
      </GlassCard>

      {!soloLectura && (
        <div className="flex flex-col items-end gap-2">
          {!meetsRequirements && (
            <div className="text-[13px] font-semibold text-danger">
              No se puede aprobar: el alumno todavía no cumple los requisitos del plan para egresar.
            </div>
          )}
          <div className="flex justify-end gap-2">
            <Button variant="outline" disabled={deciding} onClick={() => setModalRechazo(true)}>Rechazar</Button>
            <Button
              variant="gradient"
              loading={aprobar.isPending}
              disabled={deciding || cargandoProgreso || !meetsRequirements}
              onClick={confirmarAprobar}
            >
              Aprobar egreso
            </Button>
          </div>
        </div>
      )}

      {soloLectura && egreso.status === 'REJECTED' && egreso.rejectionReason && (
        <div className="text-[13px] font-semibold text-danger">Motivo del rechazo: &ldquo;{egreso.rejectionReason}&rdquo;</div>
      )}

      <ModalMotivo
        open={modalRechazo}
        onClose={() => setModalRechazo(false)}
        confirmando={rechazar.isPending}
        requerido
        onConfirmar={confirmarRechazar}
      />
    </PageContainer>
  );
}
