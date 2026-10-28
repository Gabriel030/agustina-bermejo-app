import { useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { ModalMotivo } from '@/components/ModalMotivo';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { useAuth } from '@/context/AuthContext';
import { formatMoney } from '@/lib/billing';
import { cn } from '@/lib/utils';
import {
  useApproveAuthorizationRequest,
  useAuthorizationRequest,
  useRejectAuthorizationRequest,
} from '../hooks/useAuthorizationRequests';
import { DetailBlock } from './DetailBlocks';
import { RequestTimeline } from './RequestTimeline';
import { WithdrawRequestDialog } from './WithdrawRequestDialog';
import {
  canDecideRequest,
  DECISION_REASON_MAX_LENGTH,
  displayStatus,
  dueInfo,
  formatInstant,
  inboxPath,
  isRequester,
  loadErrorMessage,
  RESUME_REJECTED,
  requesterRoleLabel,
  requestActionErrorMessage,
  roleBaseOf,
} from './authorizationRequests';

const EFFECT_DOT = {
  SUCCESS: 'bg-success',
  WARNING: 'bg-warning',
  NEUTRAL: 'bg-neutral-ink',
};

function KeyValue({ label, children }) {
  return (
    <>
      <dt className="text-xs font-semibold uppercase tracking-wide text-ink-faint">{label}</dt>
      <dd className="min-w-0 break-words text-sm text-ink">{children}</dd>
    </>
  );
}

function RequestUnavailable({ error, inboxHref, onRetry }) {
  const retryable = error?.status !== 404 && error?.status !== 403;
  return (
    <PageContainer title="Pedido de autorización">
      <GlassCard className="flex flex-col items-start gap-3 p-5">
        <p role="alert" className="text-sm text-danger">{loadErrorMessage(error)}</p>
        <div className="flex gap-2">
          {retryable && <Button type="button" variant="outline" onClick={onRetry}>Reintentar</Button>}
          <Button asChild variant="outline">
            <Link to={inboxHref}>Volver a Aprobaciones</Link>
          </Button>
        </div>
      </GlassCard>
    </PageContainer>
  );
}

function DecisionPanel({ detail, inboxHref }) {
  const { request, decidedBy, decisionReason } = detail;
  const status = displayStatus(request);
  const failed = request.resumeOutcome === RESUME_REJECTED;
  const closedLabel = { WITHDRAWN: 'Retirada por', EXPIRED: 'Cierre', APPROVED: 'Aprobada por', REJECTED: 'Rechazada por' }[request.status] ?? 'Decidió';
  const closedValue =
    request.status === 'EXPIRED' ? 'Expiró sin decisión' : [decidedBy?.name, formatInstant(request.decidedAt)].filter(Boolean).join(' · ');

  return (
    <GlassCard className="flex flex-col gap-3 p-5">
      <h2 className="font-heading text-base font-semibold text-ink">Decisión</h2>
      <dl className="grid grid-cols-[110px_1fr] items-baseline gap-x-3 gap-y-2">
        <KeyValue label="Estado"><StatusPill tone={status.tone}>{status.label}</StatusPill></KeyValue>
        <KeyValue label={closedLabel}>{closedValue || '—'}</KeyValue>
        {decisionReason && <KeyValue label={request.status === 'APPROVED' ? 'Nota' : 'Motivo'}>{decisionReason}</KeyValue>}
      </dl>
      {failed && (
        <div role="alert" className="flex items-start gap-3 rounded-2xl border border-warning/30 bg-warning-bg px-4 py-3 text-sm">
          <AlertTriangle className="mt-0.5 size-4 shrink-0 text-warning" aria-hidden="true" />
          <div className="flex flex-col gap-0.5">
            <span className="font-semibold text-ink">Aprobada, pero no se pudo ejecutar.</span>
            <span className="text-ink-soft">
              No se hizo ningún cambio en la cuenta. Volvé a cargar el pedido desde la ficha del alumno.
            </span>
          </div>
        </div>
      )}
      <Button asChild variant="outline" className="self-start">
        <Link to={inboxHref}>Volver a la bandeja</Link>
      </Button>
    </GlassCard>
  );
}

function ConsequencePanel({ detail, canDecide, note, onNoteChange }) {
  const { request, effectLines } = detail;
  const pending = request.status === 'PENDING';

  if (!pending) {
    if (request.resumeOutcome === RESUME_REJECTED) return null;
    return (
      <GlassCard className="flex flex-col gap-1 p-5">
        <h2 className="font-heading text-base font-semibold text-ink">Resultado</h2>
        <p className="text-sm text-ink-soft">
          {request.status === 'APPROVED' ? 'Se ejecutó sin problemas.' : 'No se hizo ningún cambio en la cuenta.'}
        </p>
      </GlassCard>
    );
  }

  return (
    <GlassCard className="flex flex-col gap-4 p-5">
      <h2 className="font-heading text-base font-semibold text-ink">{canDecide ? 'Si aprobás' : 'Si la dirección aprueba'}</h2>
      {effectLines.length > 0 && (
        <ul className="flex flex-col gap-2">
          {effectLines.map((line) => (
            <li key={line.text} className="flex items-start gap-2.5 text-sm text-ink-soft">
              <span className={cn('mt-1.5 size-2 shrink-0 rounded-full', EFFECT_DOT[line.tone] ?? EFFECT_DOT.NEUTRAL)} aria-hidden="true" />
              <span>{line.text}</span>
            </li>
          ))}
        </ul>
      )}
      {canDecide && (
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="approval-note">Nota (opcional)</Label>
          <Textarea
            id="approval-note"
            value={note}
            onChange={(event) => onNoteChange(event.target.value)}
            maxLength={DECISION_REASON_MAX_LENGTH}
            placeholder="Ej.: Verificado con el comprobante."
          />
        </div>
      )}
    </GlassCard>
  );
}

function ActionBar({ children, title, subtitle }) {
  return (
    <GlassCard className="sticky bottom-4 z-10 flex flex-wrap items-center gap-3 p-3 px-5">
      <div className="flex min-w-0 flex-1 flex-col">
        <span className="truncate font-semibold tabular-nums text-ink">{title}</span>
        <span className="truncate text-xs text-ink-faint">{subtitle}</span>
      </div>
      {children}
    </GlassCard>
  );
}

export function AuthorizationRequestDetailPage() {
  const { id } = useParams();
  const { usuario } = useAuth();
  const roleBase = roleBaseOf(usuario);
  const inboxHref = inboxPath(roleBase);

  const detailQuery = useAuthorizationRequest(id);
  const approve = useApproveAuthorizationRequest();
  const reject = useRejectAuthorizationRequest();

  const [note, setNote] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [withdrawOpen, setWithdrawOpen] = useState(false);

  if (detailQuery.isLoading) {
    return (
      <PageContainer title="Pedido de autorización">
        <GlassCard className="p-5"><Skeleton className="h-48 w-full" /></GlassCard>
      </PageContainer>
    );
  }

  if (detailQuery.isError || !detailQuery.data) {
    return <RequestUnavailable error={detailQuery.error} inboxHref={inboxHref} onRetry={() => detailQuery.refetch()} />;
  }

  const detail = detailQuery.data;
  const { request } = detail;
  const pending = request.status === 'PENDING';
  const own = isRequester(request, usuario);
  const canDecide = canDecideRequest(request, usuario);
  const status = displayStatus(request);
  const due = dueInfo(request.expiresOn);
  const approveLabel = request.amount ? `Aprobar ${formatMoney(request.amount)}` : 'Aprobar';
  const title = request.studentName ? `${request.operationLabel} · ${request.studentName}` : request.operationLabel;
  const requestedBy = request.requestedBy;
  const description = [
    requestedBy?.name ? `Pedido por ${requestedBy.name}` : null,
    request.requestedAt ? formatInstant(request.requestedAt) : null,
    pending && due.key !== 'none' ? `vence ${due.label.toLowerCase()}` : null,
  ]
    .filter(Boolean)
    .join(' · ');

  async function handleFailure(error, action) {
    if (error?.status !== 403) {
      const refreshed = await detailQuery.refetch();
      if (action === 'approve' && refreshed.data?.request.resumeOutcome === RESUME_REJECTED) {
        toast.error('Se aprobó, pero no se pudo ejecutar la operación. No se hizo ningún cambio en la cuenta.');
        return;
      }
    }
    toast.error(requestActionErrorMessage(error, { action, own }));
  }

  async function handleApprove() {
    if (!confirming) {
      setConfirming(true);
      return;
    }
    try {
      await approve.mutateAsync({ id: request.id, decisionReason: note.trim() });
      toast.success('Pedido aprobado.');
    } catch (error) {
      await handleFailure(error, 'approve');
    } finally {
      setConfirming(false);
    }
  }

  async function handleReject(reason) {
    try {
      await reject.mutateAsync({ id: request.id, decisionReason: reason });
      toast.success('Pedido rechazado.');
    } catch (error) {
      await handleFailure(error, 'reject');
    }
    setRejectOpen(false);
  }

  return (
    <PageContainer title={title} description={description}>
      <div className="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div className="flex min-w-0 flex-col gap-4">
          <GlassCard className="flex flex-col gap-3 p-5">
            <div className="flex items-center justify-between gap-3">
              <h2 className="font-heading text-base font-semibold text-ink">Qué se pide</h2>
              <StatusPill tone={status.tone}>{status.label}</StatusPill>
            </div>
            <dl className="grid grid-cols-[110px_1fr] items-baseline gap-x-3 gap-y-2">
              {detail.requestLines.map((line) => (
                <KeyValue key={line.label} label={line.label}>{line.value}</KeyValue>
              ))}
              <KeyValue label="Pedido por">
                {requestedBy?.name ?? '—'}
                {requestedBy?.roles?.length > 0 && <span className="text-ink-faint"> · {requesterRoleLabel(requestedBy.roles)}</span>}
              </KeyValue>
              {detail.reason && <KeyValue label="Motivo">{detail.reason}</KeyValue>}
            </dl>
          </GlassCard>

          <DetailBlock block={detail.detailBlock} />
          {!pending && <DecisionPanel detail={detail} inboxHref={inboxHref} />}
          <RequestTimeline request={request} statusChanges={detail.statusChanges} />
        </div>

        <div className="flex min-w-0 flex-col gap-4">
          <ConsequencePanel detail={detail} canDecide={canDecide} note={note} onNoteChange={setNote} />
        </div>
      </div>

      {canDecide && (
        <ActionBar
          title={request.amount ? formatMoney(request.amount) : request.operationLabel}
          subtitle={confirming ? 'Se ejecuta ahora y no se puede deshacer.' : (request.studentName ?? request.summary)}
        >
          <Button type="button" variant="outline" className="text-danger" onClick={() => setRejectOpen(true)}>
            Rechazar
          </Button>
          {confirming && (
            <Button type="button" variant="ghost" onClick={() => setConfirming(false)}>
              Volver
            </Button>
          )}
          <Button type="button" variant="gradient" loading={approve.isPending} onClick={handleApprove}>
            {confirming ? 'Confirmar aprobación' : approveLabel}
          </Button>
        </ActionBar>
      )}

      {pending && own && (
        <ActionBar
          title="Esperando a la dirección"
          subtitle={roleBase === 'director' ? 'Lo tiene que decidir otra persona de dirección.' : 'Mientras siga pendiente, podés retirarlo.'}
        >
          <Button type="button" variant="outline" className="text-danger" onClick={() => setWithdrawOpen(true)}>
            Retirar pedido
          </Button>
        </ActionBar>
      )}

      {rejectOpen && (
        <ModalMotivo
          open
          requerido
          confirmando={reject.isPending}
          titulo="Rechazar pedido"
          descripcion="Contale a quien lo pidió por qué se rechaza. Es obligatorio."
          confirmarVariant="destructive"
          onClose={() => setRejectOpen(false)}
          onConfirmar={handleReject}
        />
      )}
      {withdrawOpen && <WithdrawRequestDialog request={request} onClose={() => setWithdrawOpen(false)} />}
    </PageContainer>
  );
}
