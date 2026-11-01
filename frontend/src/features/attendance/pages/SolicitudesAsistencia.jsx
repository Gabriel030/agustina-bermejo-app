import { useState } from 'react';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { TabCount } from '@/components/TabCount';
import { ModalMotivo } from '@/components/ModalMotivo';
import { Button } from '@/components/ui/button';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useAprobarSolicitud, useRechazarSolicitud, useSolicitudesPendientes } from '@/features/attendance/hooks/useSolicitudesAsistencia';
import {
  useAprobarJustificacion,
  useJustificacionesPendientes,
  useRechazarJustificacion,
} from '@/features/attendance/hooks/useJustificacionesAsistencia';

const ESTADO_LABEL = { PRESENT: 'Presente', LATE: 'Tarde', ABSENT: 'Ausente', JUSTIFICATION_PENDING: 'Justificación pendiente', JUSTIFIED: 'Justificada' };
const MIN_REJECTION_REASON_LENGTH = 10;
const TABS = [
  { value: 'all', label: 'Todas' },
  { value: 'correction', label: 'Correcciones' },
  { value: 'justification', label: 'Justificaciones' },
];

function isHttpUrl(value) {
  return typeof value === 'string' && /^https?:\/\//i.test(value);
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
    attachmentUrl: null,
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
    attachmentUrl: j.attachmentUrl,
  };
}

function RequestCard({ item, busy, onApprove, onReject }) {
  return (
    <GlassCard className="flex flex-col gap-3 p-4" data-testid={`attendance-request-${item.key}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="font-heading text-[14.5px] font-semibold text-ink">{item.title}</div>
        <div className="flex items-center gap-2">
          <StatusPill tone={item.kind === 'justification' ? 'accent' : 'neutral'}>
            {item.kind === 'justification' ? 'Justificación' : 'Corrección'}
          </StatusPill>
          <span className="text-[12.5px] text-ink-faint">{item.caption}</span>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-[12.5px] sm:grid-cols-4">
        {item.fields.map((f) => (
          <div key={f.label}>
            <div className="text-ink-faint">{f.label}</div>
            <div className="font-medium text-ink">{f.value}</div>
          </div>
        ))}
      </div>

      <div>
        <div className="text-[12.5px] text-ink-faint">Motivo</div>
        <div className="text-[13px] text-ink">{item.reason}</div>
        {item.kind === 'justification' &&
          (isHttpUrl(item.attachmentUrl) ? (
            <a href={item.attachmentUrl} target="_blank" rel="noopener noreferrer" className="text-[12.5px] font-bold text-brand hover:underline">
              Ver adjunto
            </a>
          ) : (
            <div className="text-[12.5px] text-ink-faint">Sin adjunto</div>
          ))}
      </div>

      <div className="flex justify-end gap-2">
        <Button variant="outline" size="sm" onClick={() => onReject(item)} disabled={busy}>Rechazar</Button>
        <Button variant="secondary" size="sm" onClick={() => onApprove(item)} disabled={busy}>Aprobar</Button>
      </div>
    </GlassCard>
  );
}

export function SolicitudesAsistencia() {
  const corrections = useSolicitudesPendientes();
  const justifications = useJustificacionesPendientes();
  const approveCorrection = useAprobarSolicitud();
  const rejectCorrection = useRechazarSolicitud();
  const approveJustification = useAprobarJustificacion();
  const rejectJustification = useRechazarJustificacion();
  const [tab, setTab] = useState('all');
  const [rejecting, setRejecting] = useState(null);

  const correctionItems = (corrections.data ?? []).map(toRequestItem);
  const justificationItems = (justifications.data?.items ?? []).map(toJustificationItem);
  const counts = {
    all: correctionItems.length + justificationItems.length,
    correction: correctionItems.length,
    justification: justificationItems.length,
  };
  const visible = {
    all: [...correctionItems, ...justificationItems],
    correction: correctionItems,
    justification: justificationItems,
  }[tab];
  const isLoading = corrections.isLoading || justifications.isLoading;
  const busy =
    approveCorrection.isPending || rejectCorrection.isPending || approveJustification.isPending || rejectJustification.isPending;

  async function approve(item) {
    try {
      if (item.kind === 'justification') await approveJustification.mutateAsync(item.ref);
      else await approveCorrection.mutateAsync(item.ref);
      toast.success(item.kind === 'justification' ? 'Justificación aprobada' : 'Solicitud aprobada');
    } catch (error) {
      toast.error(error.message);
    }
  }

  async function confirmReject(reason) {
    try {
      if (rejecting.kind === 'justification') await rejectJustification.mutateAsync({ attendanceId: rejecting.ref, reason });
      else await rejectCorrection.mutateAsync(rejecting.ref);
      toast.success(rejecting.kind === 'justification' ? 'Justificación rechazada' : 'Solicitud rechazada');
      setRejecting(null);
      return true;
    } catch (error) {
      toast.error(error.message);
      return false;
    }
  }

  const rejectingJustification = rejecting?.kind === 'justification';

  return (
    <PageContainer>
      <div>
        <h1 className="font-heading text-2xl font-bold text-ink">Solicitudes de asistencia</h1>
        <p className="mt-0.5 text-sm text-ink-soft">Correcciones pedidas por docentes y justificaciones de inasistencia</p>
      </div>

      <Tabs value={tab} onValueChange={setTab}>
        <TabsList variant="segmented" className="w-full sm:w-fit">
          {TABS.map((t) => (
            <TabsTrigger key={t.value} value={t.value} className="flex-1">
              {t.label}
              <TabCount count={counts[t.value]} />
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      {isLoading ? (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-16" />
          <Skeleton className="h-16" />
        </div>
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

      <ModalMotivo
        open={rejecting !== null}
        onClose={() => setRejecting(null)}
        confirmando={rejectCorrection.isPending || rejectJustification.isPending}
        requerido={rejectingJustification}
        minLength={rejectingJustification ? MIN_REJECTION_REASON_LENGTH : 0}
        titulo={rejectingJustification ? 'Rechazar justificación' : 'Rechazar solicitud'}
        descripcion={
          rejectingJustification
            ? 'El alumno recibe este motivo y la inasistencia sigue como Ausente.'
            : 'La asistencia queda como estaba.'
        }
        placeholder="Explicá por qué se rechaza"
        confirmarVariant="destructive"
        onConfirmar={confirmReject}
      />
    </PageContainer>
  );
}
