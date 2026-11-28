import { useState } from 'react';
import { Undo2 } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { apiFetchBlob } from '@/api/client';
import { describeError } from '@/api/describeError';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { LoadErrorState } from '@/components/LoadErrorState';
import { StatusPill } from '@/components/StatusPill';
import { TabCount } from '@/components/TabCount';
import { ChoiceChip } from '@/components/ChoiceChip';
import { ModalMotivo } from '@/components/ModalMotivo';
import { Button } from '@/components/ui/button';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useAprobarSolicitud, useRechazarSolicitud, useSolicitudesPendientes } from '@/features/attendance/hooks/useSolicitudesAsistencia';
import {
  JUSTIFICATIONS_PAGE_SIZE,
  useAprobarJustificacion,
  useJustificacionesPendientes,
  useRechazarJustificacion,
} from '@/features/attendance/hooks/useJustificacionesAsistencia';
import {
  RESOLVED_PAGE_SIZE,
  useResolvedAttendanceRequests,
  useRevertAttendanceDecision,
} from '@/features/attendance/hooks/useResolvedAttendanceRequests';
import { formatDayKey, revertDialogCopy } from '@/features/attendance/lib/attendanceDecisionRevert';

const ESTADO_LABEL = { PRESENT: 'Presente', LATE: 'Tarde', ABSENT: 'Ausente', JUSTIFICATION_PENDING: 'Justificación pendiente', JUSTIFIED: 'Justificada' };
const MIN_REJECTION_REASON_LENGTH = 10;
const PENDING_FILTERS = [
  { value: 'all', label: 'Todas' },
  { value: 'correction', label: 'Correcciones' },
  { value: 'justification', label: 'Justificaciones' },
];

const RESOLVED_FILTERS = [
  { value: 'all', label: 'Todas', result: undefined },
  { value: 'approved', label: 'Aprobadas', result: 'APPROVED' },
  { value: 'rejected', label: 'Rechazadas', result: 'REJECTED' },
];

const RESOLVED_EMPTY = {
  all: { title: 'Todavía no hay solicitudes resueltas', description: 'Las solicitudes que apruebes o rechacen aparecen acá.' },
  approved: { title: 'Ninguna solicitud aprobada', description: 'Probá con otro resultado.' },
  rejected: { title: 'Ninguna solicitud rechazada', description: 'Probá con otro resultado.' },
};

const RESULT_PILL = {
  APPROVED: { tone: 'success', label: 'Aprobada', verb: 'Aprobada' },
  REJECTED: { tone: 'danger', label: 'Rechazada', verb: 'Rechazada' },
};

const TAB_PARAM_TO_VALUE = { todas: 'all', correcciones: 'correction', justificaciones: 'justification' };

async function openJustificationAttachment(attendanceId) {
  const preview = window.open('', '_blank');
  try {
    const { blob } = await apiFetchBlob(`/attendance/${attendanceId}/justification-attachment`);
    const url = URL.createObjectURL(blob);
    if (preview) {
      preview.location.href = url;
    } else {
      window.location.assign(url);
    }
  } catch {
    preview?.close();
    toast.error('No pudimos abrir el comprobante. Probá de nuevo.');
  }
}

function actionErrorMessage(action, error) {
  const detail = error?.message ? ` ${error.message}` : '';
  return `No pudimos ${action}.${detail} Revisá la lista y probá de nuevo.`;
}

function toRequestItem(s) {
  return {
    key: `correction-${s.id}`,
    kind: 'correction',
    ref: s.id,
    title: `${s.subjectName} · ${s.sectionName}`,
    caption: `Solicitud #${s.id}`,
    fields: [
      { label: 'Docente', value: s.requestedByName },
      { label: 'Alumno', value: s.studentName },
      { label: 'Clase', value: s.classDate },
      { label: 'Cambio pedido', value: `${ESTADO_LABEL[s.previousStatus] ?? s.previousStatus} → ${ESTADO_LABEL[s.requestedStatus] ?? s.requestedStatus}` },
    ],
    reason: s.reason,
    hasAttachment: false,
    reversal: s.revertedAt ? { byName: s.revertedByName, at: s.revertedAt, reason: s.revertReason } : null,
  };
}

function toJustificationItem(j) {
  return {
    key: `justification-${j.attendanceId}`,
    kind: 'justification',
    ref: j.attendanceId,
    title: `${j.subjectName} · ${j.sectionName}`,
    caption: `Inasistencia #${j.attendanceId}`,
    fields: [
      { label: 'Alumno', value: j.studentName },
      { label: 'Clase', value: j.classDate },
      { label: 'Cambio pedido', value: 'Ausente → Justificada' },
    ],
    reason: j.reason,
    hasAttachment: Boolean(j.attachmentUrl),
  };
}

function toResolvedItem(r) {
  const base = toRequestItem(r);
  const justification = r.requestedStatus === 'JUSTIFIED';
  return {
    ...base,
    key: `resolved-${r.id}`,
    kind: justification ? 'justification' : 'correction',
    resolution: {
      result: r.status,
      decidedByName: r.decidedByName,
      decidedAt: r.decidedAt,
      rejectionReason: r.rejectionReason,
      requestId: r.id,
      revertible: r.revertible === true,
      revertibleUntil: r.revertibleUntil,
      studentName: r.studentName,
      previousStatusLabel: ESTADO_LABEL[r.previousStatus] ?? r.previousStatus,
    },
  };
}

function ReversalLine({ reversal, prefix }) {
  return (
    <div className="text-size-12-5 text-ink-soft" data-testid="reversal-line">
      {prefix} {reversal.byName ?? 'sistema'} · {formatDayKey(reversal.at)}
      {reversal.reason ? `: ${reversal.reason}` : ''}
    </div>
  );
}

function ResolutionFooter({ item, onRevert }) {
  const { resolution } = item;
  const pill = RESULT_PILL[resolution.result];
  const deadline = formatDayKey(resolution.revertibleUntil);
  const canRevert = resolution.revertible;
  return (
    <div className="flex flex-col gap-2 border-t border-border pt-3" data-testid="resolution-footer">
      <div className="flex flex-wrap items-center gap-2 text-size-12-5 text-ink-soft">
        {pill && <StatusPill tone={pill.tone}>{pill.label}</StatusPill>}
        <span>
          {pill?.verb} por {resolution.decidedByName ?? 'sistema'} · {formatDayKey(resolution.decidedAt)}
        </span>
      </div>
      {item.reversal && <ReversalLine reversal={item.reversal} prefix="Revertida antes por" />}
      {resolution.result === 'REJECTED' && resolution.rejectionReason && (
        <div className="text-size-12-5">
          <span className="text-ink-faint">Motivo del rechazo: </span>
          <span className="text-ink">{resolution.rejectionReason}</span>
        </div>
      )}
      {canRevert ? (
        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            className="min-h-11 sm:min-h-0"
            aria-label={`Revertir, hasta el fin del día ${deadline}`}
            onClick={() => onRevert(item)}
          >
            <Undo2 className="size-3.5" aria-hidden="true" />
            Revertir
          </Button>
          <span className="text-size-12-5 text-ink-faint">Revertible hasta el fin del día {deadline}</span>
        </div>
      ) : (
        <div className="text-size-12-5 text-ink-faint">Pasaron más de 7 días: ya no se puede revertir.</div>
      )}
    </div>
  );
}

function RequestCard({ item, busy, onApprove, onReject, onRevert }) {
  return (
    <GlassCard className="flex flex-col gap-3 p-4" data-testid={`attendance-request-${item.key}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="font-heading text-size-14-5 font-semibold text-ink">{item.title}</div>
        <div className="flex items-center gap-2">
          <StatusPill tone={item.kind === 'justification' ? 'accent' : 'neutral'}>
            {item.kind === 'justification' ? 'Justificación' : 'Corrección'}
          </StatusPill>
          {item.reversal && !item.resolution && <StatusPill tone="warning">Revertida</StatusPill>}
          <span className="text-size-12-5 text-ink-faint">{item.caption}</span>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-size-12-5 sm:grid-cols-4">
        {item.fields.map((f) => (
          <div key={f.label}>
            <div className="text-ink-faint">{f.label}</div>
            <div className="font-medium text-ink">{f.value}</div>
          </div>
        ))}
      </div>

      <div>
        <div className="text-size-12-5 text-ink-faint">Motivo</div>
        <div className="text-size-13 text-ink">{item.reason}</div>
        {item.kind === 'justification' &&
          (item.hasAttachment ? (
            <Button
              variant="link"
              size="inline"
              type="button"
              onClick={() => openJustificationAttachment(item.ref)}
              className="text-size-12-5 font-bold text-brand"
              >
              Ver adjunto
            </Button>
          ) : (
            <div className="text-size-12-5 text-ink-faint">Sin adjunto</div>
          ))}
      </div>

      {item.reversal && !item.resolution && <ReversalLine reversal={item.reversal} prefix="Revertida por" />}

      {item.resolution ? (
        <ResolutionFooter item={item} onRevert={onRevert} />
      ) : (
        <div className="flex justify-end gap-2">
          <Button variant="outline" size="sm" className="min-h-11 flex-1 sm:min-h-0 sm:flex-none" onClick={() => onReject(item)} disabled={busy}>Rechazar</Button>
          <Button variant="secondary" size="sm" className="min-h-11 flex-1 sm:min-h-0 sm:flex-none" onClick={() => onApprove(item)} disabled={busy}>Aprobar</Button>
        </div>
      )}
    </GlassCard>
  );
}

export function SolicitudesAsistencia() {
  const corrections = useSolicitudesPendientes();
  const [justificationSize, setJustificationSize] = useState(JUSTIFICATIONS_PAGE_SIZE);
  const justifications = useJustificacionesPendientes(justificationSize);
  const approveCorrection = useAprobarSolicitud();
  const rejectCorrection = useRechazarSolicitud();
  const approveJustification = useAprobarJustificacion();
  const rejectJustification = useRechazarJustificacion();
  const revertDecision = useRevertAttendanceDecision();
  const [searchParams] = useSearchParams();
  const tabParam = searchParams.get('tab');
  const [section, setSection] = useState(tabParam === 'resueltas' ? 'resolved' : 'pending');
  const [tab, setTab] = useState(TAB_PARAM_TO_VALUE[tabParam] ?? 'all');
  const [resolvedFilter, setResolvedFilter] = useState('all');
  const [resolvedSize, setResolvedSize] = useState(RESOLVED_PAGE_SIZE);
  const [rejecting, setRejecting] = useState(null);
  const [reverting, setReverting] = useState(null);
  const resolvedResult = RESOLVED_FILTERS.find((filter) => filter.value === resolvedFilter).result;
  const resolved = useResolvedAttendanceRequests({ result: resolvedResult, size: resolvedSize });

  const correctionItems = (corrections.data ?? []).map(toRequestItem);
  const justificationItems = (justifications.data?.items ?? []).map(toJustificationItem);
  const justificationTotal = justifications.data?.totalElements ?? justificationItems.length;
  const hiddenJustifications = Math.max(0, justificationTotal - justificationItems.length);
  const pendingTotal = correctionItems.length + justificationTotal;
  const visible = {
    all: [...correctionItems, ...justificationItems],
    correction: correctionItems,
    justification: justificationItems,
  }[tab];
  const resolvedItems = (resolved.data?.items ?? []).map(toResolvedItem);
  const resolvedTotal = resolved.data?.totalElements ?? resolvedItems.length;
  const hiddenResolved = Math.max(0, resolvedTotal - resolvedItems.length);
  const showingResolved = section === 'resolved';
  const isLoading = showingResolved ? resolved.isLoading : corrections.isLoading || justifications.isLoading;
  const failed = (showingResolved ? [resolved] : [corrections, justifications]).find((query) => query.isError && query.data === undefined);
  const busy =
    approveCorrection.isPending || rejectCorrection.isPending || approveJustification.isPending || rejectJustification.isPending;

  async function approve(item) {
    try {
      if (item.kind === 'justification') await approveJustification.mutateAsync(item.ref);
      else await approveCorrection.mutateAsync(item.ref);
      toast.success(item.kind === 'justification' ? 'Justificación aprobada' : 'Solicitud aprobada');
    } catch (error) {
      toast.error(actionErrorMessage(item.kind === 'justification' ? 'aprobar la justificación' : 'aprobar la solicitud', error));
    }
  }

  async function confirmReject(reason) {
    try {
      if (rejecting.kind === 'justification') await rejectJustification.mutateAsync({ attendanceId: rejecting.ref, reason });
      else await rejectCorrection.mutateAsync({ id: rejecting.ref, reason });
      toast.success(rejecting.kind === 'justification' ? 'Justificación rechazada' : 'Solicitud rechazada');
      setRejecting(null);
      return true;
    } catch (error) {
      toast.error(actionErrorMessage(rejecting.kind === 'justification' ? 'rechazar la justificación' : 'rechazar la solicitud', error));
      return false;
    }
  }

  async function confirmRevert(reason) {
    try {
      await revertDecision.mutateAsync({ id: reverting.resolution.requestId, reason });
      toast.success('Decisión revertida. La solicitud volvió a Pendientes.');
    } catch (error) {
      toast.error(describeError(error).message);
    }
    setReverting(null);
    return true;
  }

  const rejectingJustification = rejecting?.kind === 'justification';
  const revertCopy = reverting
    ? revertDialogCopy({
        kind: reverting.kind,
        result: reverting.resolution.result,
        studentName: reverting.resolution.studentName,
        previousStatusLabel: reverting.resolution.previousStatusLabel,
      })
    : null;
  const resolvedEmpty = RESOLVED_EMPTY[resolvedFilter];

  return (
    <PageContainer>
      <div>
        <h1 className="font-heading text-2xl font-bold text-ink">Solicitudes de asistencia</h1>
        <p className="mt-0.5 text-sm text-ink-soft">Correcciones pedidas por docentes y justificaciones de inasistencia</p>
      </div>

      <Tabs value={section} onValueChange={setSection}>
        <TabsList variant="line">
          <TabsTrigger value="pending" className="flex-1">
            Pendientes
            <TabCount count={pendingTotal} />
          </TabsTrigger>
          <TabsTrigger value="resolved" className="flex-1">Resueltas</TabsTrigger>
        </TabsList>
      </Tabs>

      <div className="flex flex-wrap gap-2" role="group" aria-label={showingResolved ? 'Filtrar por resultado' : 'Filtrar por tipo'}>
        {(showingResolved ? RESOLVED_FILTERS : PENDING_FILTERS).map((filter) => (
          <ChoiceChip
            key={filter.value}
            selected={(showingResolved ? resolvedFilter : tab) === filter.value}
            onClick={() => (showingResolved ? setResolvedFilter(filter.value) : setTab(filter.value))}
          >
            {filter.label}
          </ChoiceChip>
        ))}
      </div>

      {isLoading ? (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-16" />
          <Skeleton className="h-16" />
        </div>
      ) : failed ? (
        <LoadErrorState error={failed.error} resource="las solicitudes" onRetry={() => failed.refetch()} />
      ) : showingResolved ? (
        resolvedItems.length === 0 ? (
          <GlassCard className="p-4">
            <EmptyState title={resolvedEmpty.title} description={resolvedEmpty.description} />
          </GlassCard>
        ) : (
          <div className="flex flex-col gap-3">
            {resolvedItems.map((item) => (
              <RequestCard key={item.key} item={item} busy={false} onRevert={setReverting} />
            ))}
          </div>
        )
      ) : visible.length === 0 ? (
        <GlassCard className="p-4">
          <EmptyState title="Sin solicitudes pendientes" description="Cuando haya una corrección o una justificación, aparece acá." />
        </GlassCard>
      ) : (
        <div className="flex flex-col gap-3">
          {visible.map((item) => (
            <RequestCard key={item.key} item={item} busy={busy} onApprove={approve} onReject={setRejecting} />
          ))}
        </div>
      )}

      {!isLoading && !showingResolved && tab !== 'correction' && justificationTotal > 0 && (
        <div className="flex flex-col items-center gap-2 text-size-12-5 text-ink-soft" data-testid="justifications-pagination">
          <span>Mostrando {justificationItems.length} de {justificationTotal} justificaciones</span>
          {hiddenJustifications > 0 && (
            <Button
              variant="outline"
              size="sm"
              disabled={justifications.isFetching}
              onClick={() => setJustificationSize((size) => size + JUSTIFICATIONS_PAGE_SIZE)}
            >
              Ver más
            </Button>
          )}
        </div>
      )}

      {!isLoading && showingResolved && resolvedTotal > 0 && (
        <div className="flex flex-col items-center gap-2 text-size-12-5 text-ink-soft" data-testid="resolved-pagination">
          <span>Mostrando {resolvedItems.length} de {resolvedTotal} solicitudes</span>
          {hiddenResolved > 0 && (
            <Button
              variant="outline"
              size="sm"
              disabled={resolved.isFetching}
              onClick={() => setResolvedSize((size) => size + RESOLVED_PAGE_SIZE)}
            >
              Ver más
            </Button>
          )}
        </div>
      )}

      <ModalMotivo
        open={rejecting !== null}
        onClose={() => setRejecting(null)}
        confirmando={rejectCorrection.isPending || rejectJustification.isPending}
        requerido
        minLength={MIN_REJECTION_REASON_LENGTH}
        showCounter
        titulo={rejectingJustification ? 'Rechazar justificación' : 'Rechazar solicitud'}
        descripcion={
          rejectingJustification
            ? 'El alumno recibe este motivo y la inasistencia sigue como Ausente.'
            : 'El docente recibe este motivo y la asistencia queda como estaba.'
        }
        placeholder="Explicá por qué se rechaza"
        confirmarVariant="destructive"
        onConfirmar={confirmReject}
      />

      <ModalMotivo
        open={reverting !== null}
        onClose={() => setReverting(null)}
        confirmando={revertDecision.isPending}
        requerido
        minLength={MIN_REJECTION_REASON_LENGTH}
        showCounter
        titulo={revertCopy?.title ?? 'Revertir decisión'}
        descripcion={revertCopy?.description}
        placeholder={revertCopy?.placeholder}
        confirmarLabel="Revertir"
        confirmarVariant="primary"
        mensajeRequerido="El motivo de la reversión es obligatorio."
        onConfirmar={confirmRevert}
      />
    </PageContainer>
  );
}
