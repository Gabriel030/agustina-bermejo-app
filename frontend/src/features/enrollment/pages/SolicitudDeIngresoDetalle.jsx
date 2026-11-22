import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft, CheckCircle2, FileText, ShieldCheck, TriangleAlert, User } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { Notice } from '@/components/Notice';
import { StickyActionBar } from '@/components/StickyActionBar';
import { ModalMotivo } from '@/components/ModalMotivo';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { UnavailableRequestState } from '@/components/director/UnavailableRequestState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { describeError } from '@/api/describeError';
import { useReviewBasePath } from '@/features/enrollment/hooks/useReviewBasePath';
import {
  fetchApplicationDocument,
  useApplicationForReview,
  useApprovalCheck,
  useApproveApplication,
  usePendingApplications,
  useRejectApplication,
} from '@/features/enrollment/hooks/useApplicationReview';
import {
  APPLICATION_REVIEW_PATH,
  APPLICATION_STATUS,
  DOCUMENT_TYPE_LABEL,
  REQUIRED_DOCUMENT_TYPES,
  approvalBlockReason,
  blockingReasonMessages,
  cohortLabel,
  debtMessages,
  documentationLabel,
  isApplicationAlreadyResolved,
  missingDocumentsText,
  nextPendingId,
} from '@/features/enrollment/lib/applicationReview';
import { formatMoney } from '@/lib/billing';
import { formatearFecha } from '@/lib/comisiones';
import { ageLabel, MIN_REJECTION_REASON_LENGTH } from '@/lib/aprobaciones';

function DetailTerm({ label, children }) {
  return (
    <div className="min-w-0">
      <dt className="text-size-12 font-bold uppercase tracking-wide text-ink-faint">{label}</dt>
      <dd className="mt-0.5 break-words font-bold text-ink tabular-nums">{children ?? '—'}</dd>
    </div>
  );
}

function CardTitle({ icon: Icon, id, children }) {
  return (
    <h2 id={id} className="flex items-center gap-2 font-heading text-size-15 font-semibold text-ink">
      <Icon className="size-[18px] text-primary" aria-hidden="true" />
      {children}
    </h2>
  );
}

async function openDocument(applicationId, documentId) {
  const preview = window.open('', '_blank');
  try {
    const blob = await fetchApplicationDocument(applicationId, documentId);
    const url = URL.createObjectURL(blob);
    if (preview) {
      preview.location.href = url;
    } else {
      window.location.assign(url);
    }
  } catch {
    preview?.close();
    toast.error('No pudimos abrir el documento. Probá de nuevo.');
  }
}

function DocumentationCard({ application }) {
  const documentation = application.documentation;
  const documents = documentation?.documents ?? [];
  return (
    <GlassCard className="flex flex-col gap-2 p-4" aria-labelledby="documentation-title">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <CardTitle icon={FileText} id="documentation-title">
          Documentación
        </CardTitle>
        {documentation && (
          <StatusPill tone={documentation.complete ? 'success' : 'warning'}>
            {documentationLabel(documentation)}
          </StatusPill>
        )}
      </div>
      <ul className="flex flex-col">
        {REQUIRED_DOCUMENT_TYPES.map((type) => {
          const document = documents.find((candidate) => candidate.type === type);
          const label = DOCUMENT_TYPE_LABEL[type];
          return (
            <li key={type} className="flex flex-wrap items-center gap-3 border-b border-border py-2.5 last:border-b-0">
              <div className="min-w-40 flex-1">
                <b className="block text-size-14 text-ink">{label}</b>
                <small className="text-size-12 text-ink-faint">
                  {document ? `Subido el ${formatearFecha(document.uploadedAt) ?? '—'}` : 'Todavía no lo subió'}
                </small>
              </div>
              {document ? (
                <>
                  <StatusPill tone="success">Cargado</StatusPill>
                  <Button
                    type="button"
                    variant="outline"
                    className="min-h-11 px-4 font-bold"
                    aria-label={`Ver ${label}`}
                    onClick={() => openDocument(application.id, document.id)}
                  >
                    Ver
                  </Button>
                </>
              ) : (
                <StatusPill tone="warning">Falta</StatusPill>
              )}
            </li>
          );
        })}
      </ul>
    </GlassCard>
  );
}

function CheckRow({ ok, title, children }) {
  return (
    <div className="flex items-start gap-3 py-2">
      <span
        aria-hidden="true"
        className={`grid size-7 shrink-0 place-items-center rounded-full text-size-12 font-bold ${ok ? 'bg-success-bg text-success-ink' : 'bg-danger-bg text-danger-ink'}`}
      >
        {ok ? '✓' : '✕'}
      </span>
      <div className="flex flex-col items-start gap-1">
        <div className="flex flex-wrap items-center gap-2">
          <b className="text-size-14 text-ink">{title}</b>
          <StatusPill tone={ok ? 'success' : 'danger'}>{ok ? 'En regla' : 'Bloquea'}</StatusPill>
        </div>
        {children}
      </div>
    </div>
  );
}

function BeforeApprovingCard({ check, application, basePath }) {
  if (check.isLoading) {
    return (
      <GlassCard className="flex flex-col gap-2 p-4" aria-busy="true" aria-label="Verificando condiciones">
        <Skeleton className="h-6 w-48" />
        <Skeleton className="h-12" />
      </GlassCard>
    );
  }
  if (check.isError) {
    return (
      <GlassCard className="flex flex-wrap items-center gap-3 p-4">
        <span role="alert" className="text-size-13-5 text-ink-soft">
          No pudimos verificar la documentación y la deuda del alumno.
        </span>
        <Button type="button" variant="outline" className="min-h-11" onClick={() => check.refetch()}>
          Reintentar
        </Button>
      </GlassCard>
    );
  }
  const data = check.data;
  const documentationOk = data.documentation?.complete;
  const debtBlocks = data.debt?.blocksApproval;
  const studentPath = `${basePath}/alumnos/${application.studentId}`;
  return (
    <GlassCard className="flex flex-col gap-1 p-4" aria-labelledby="before-approving-title">
      <CardTitle icon={ShieldCheck} id="before-approving-title">
        Antes de aprobar
      </CardTitle>
      <CheckRow ok={documentationOk} title="Documentación obligatoria">
        <p className="text-size-12-5 text-ink-faint">
          {documentationOk
            ? 'DNI (frente y dorso) y analítico cargados.'
            : `Falta ${missingDocumentsText(data.documentation?.missingTypes)}. Hasta que la suba no se puede aprobar.`}
        </p>
        {!documentationOk && (
          <Button asChild variant="link" size="inline" className="min-h-11">
            <Link to={studentPath}>Cargarlo en mostrador desde el legajo</Link>
          </Button>
        )}
      </CheckRow>
      {blockingReasonMessages(data).length > 0 && (
        <CheckRow ok={false} title="Estado de la solicitud y del alumno">
          {blockingReasonMessages(data).map((message) => (
            <p key={message} className="text-size-12-5 text-ink-faint">
              {message}
            </p>
          ))}
        </CheckRow>
      )}
      <CheckRow ok={!debtBlocks} title="Deuda del alumno">
        {debtBlocks ? (
          <>
            {debtMessages(data.debt).map((message) => (
              <p key={message} className="text-size-12-5 text-ink-faint">
                {message}
              </p>
            ))}
            <p className="text-size-12-5 text-ink-faint">Tiene que regularizarla antes de que se apruebe el ingreso.</p>
            {['/administrativo', '/subdirector'].includes(basePath) && (
              <Button asChild variant="link" size="inline" className="min-h-11">
                <Link to={`${studentPath}?view=financiero`}>Ir al Financiero del alumno</Link>
              </Button>
            )}
          </>
        ) : (
          <p className="text-size-12-5 text-ink-faint">Sin deuda que bloquee la inscripción.</p>
        )}
      </CheckRow>
    </GlassCard>
  );
}

function ResolutionCard({ application }) {
  const invoice = application.admissionInvoice;
  return (
    <GlassCard className="flex flex-col gap-3 p-4" aria-labelledby="resolution-title">
      <CardTitle icon={CheckCircle2} id="resolution-title">
        Resolución
      </CardTitle>
      <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <DetailTerm label="Resuelta por">{application.decidedByName}</DetailTerm>
        <DetailTerm label="Fecha">{formatearFecha(application.decidedAt)}</DetailTerm>
      </dl>
      {application.status === 'REJECTED' && application.rejectionReason && (
        <Notice tone="danger" role="note" className="text-size-13-5 text-danger-ink">
          <b>Motivo del rechazo (lo ve el alumno):</b> {application.rejectionReason}
        </Notice>
      )}
      {application.status === 'APPROVED' && invoice && (
        <Notice tone="success" role="note" className="text-size-13-5 text-success-ink">
          Matrícula de ingreso · <b className="tabular-nums">{formatMoney(invoice.total)}</b>
          {invoice.dueDate ? ` · vence ${formatearFecha(invoice.dueDate)}` : ''}
        </Notice>
      )}
      {application.status === 'EXPIRED' && (
        <Notice tone="neutral" role="note" className="text-size-13-5">
          La matrícula de ingreso no se pagó a tiempo: la aprobación venció.
        </Notice>
      )}
    </GlassCard>
  );
}

export function SolicitudDeIngresoDetalle() {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const basePath = useReviewBasePath();
  const listPath = `${basePath}/${APPLICATION_REVIEW_PATH}`;
  const headingRef = useRef(null);
  const applicationQuery = useApplicationForReview(id);
  const application = applicationQuery.data;
  const isPending = application?.status === 'PENDING';
  const pendingList = usePendingApplications();
  const check = useApprovalCheck(id, isPending);
  const approve = useApproveApplication(id);
  const reject = useRejectApplication(id);
  const [approveOpen, setApproveOpen] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [openNext, setOpenNext] = useState(true);
  const [conflictId, setConflictId] = useState(null);
  const conflict = conflictId === id;

  const advanced = location.state?.advanced === true;
  const loadedId = application?.id;
  useEffect(() => {
    if (advanced && loadedId) headingRef.current?.focus();
  }, [advanced, loadedId]);

  if (applicationQuery.isError && applicationQuery.error?.status === 403) {
    return (
      <PageContainer>
        <LoadErrorState distinguishForbidden error={applicationQuery.error} backTo={listPath} backLabel="Solicitudes de ingreso" />
      </PageContainer>
    );
  }

  if (applicationQuery.isError) {
    return (
      <UnavailableRequestState
        error={applicationQuery.error}
        backTo={listPath}
        backLabel="Solicitudes de ingreso"
        onRetry={applicationQuery.refetch}
      />
    );
  }

  if (applicationQuery.isLoading || !application) {
    return (
      <PageContainer>
        <div aria-busy="true" aria-label="Cargando">
          <Skeleton className="h-10 w-64" />
        </div>
        <Skeleton className="h-40" />
      </PageContainer>
    );
  }

  const status = APPLICATION_STATUS[application.status] ?? { label: application.status, tone: 'neutral' };
  const nextId = nextPendingId(pendingList.data, application.id);
  const busy = approve.isPending || reject.isPending;
  const blockReason = check.isLoading ? 'Verificando condiciones…' : approvalBlockReason(check.data);
  const showActions = isPending && !conflict;
  const awaitingRefresh = conflict && isPending;
  const age = ageLabel(application.requestedAt);

  function handleConflict() {
    setConflictId(id);
    setApproveOpen(false);
    setRejectOpen(false);
    applicationQuery.refetch();
  }

  async function handleApprove() {
    try {
      await approve.mutateAsync();
      setApproveOpen(false);
      if (openNext && nextId) {
        toast.success('Solicitud aprobada. Abrimos la siguiente.');
        navigate(`${listPath}/${nextId}`, { state: { advanced: true } });
      } else {
        toast.success('Solicitud aprobada. Se generó la matrícula de ingreso.');
        navigate(listPath);
      }
    } catch (failure) {
      if (isApplicationAlreadyResolved(failure)) {
        handleConflict();
        return;
      }
      setApproveOpen(false);
      toast.error(describeError(failure, { fallback: 'No pudimos aprobar la solicitud. Probá de nuevo.' }).message);
      check.refetch();
    }
  }

  async function handleReject(reason) {
    try {
      await reject.mutateAsync(reason);
      setRejectOpen(false);
      toast.success('Solicitud rechazada. El alumno verá el motivo.');
      navigate(listPath);
      return true;
    } catch (failure) {
      if (isApplicationAlreadyResolved(failure)) {
        handleConflict();
        return false;
      }
      toast.error(describeError(failure, { fallback: 'No pudimos rechazar la solicitud. Probá de nuevo.' }).message);
      return false;
    }
  }

  const cohort = cohortLabel(application.programCohortName);

  return (
    <PageContainer>
      <nav aria-label="Ruta">
        <Link
          to={listPath}
          className="inline-flex min-h-11 w-fit items-center gap-1.5 text-size-13 font-semibold text-primary hover:underline"
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
          Solicitudes de ingreso
        </Link>
      </nav>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 ref={headingRef} tabIndex={-1} className="font-heading text-2xl font-bold text-ink outline-none">
            {application.studentFullName ?? 'Solicitud de ingreso'}
          </h1>
          <p className="mt-0.5 text-sm text-ink-faint">
            {application.programName}
            {cohort ? ` · ${cohort}` : ''}
          </p>
        </div>
        <StatusPill tone={awaitingRefresh ? 'neutral' : status.tone}>{awaitingRefresh ? 'Estado actualizado' : status.label}</StatusPill>
      </div>

      <div role="status" aria-live="polite">
        {conflict && (
          <div className="flex flex-wrap items-center gap-3 rounded-card border border-warning bg-warning-bg px-4 py-3 text-size-13-5 font-medium text-warning-ink">
            <TriangleAlert className="size-[18px] shrink-0" aria-hidden="true" />
            <span>
              <b>Otra persona ya resolvió esta solicitud.</b> No se hizo ningún cambio.
            </span>
            <Button asChild variant="outline" className="min-h-11 px-4 font-bold">
              <Link to={listPath}>Volver a la bandeja</Link>
            </Button>
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[3fr_2fr]">
        <div className="flex min-w-0 flex-col gap-4">
          <GlassCard className="flex flex-col gap-3 p-4" aria-labelledby="applicant-title">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <CardTitle icon={User} id="applicant-title">
                Solicitante
              </CardTitle>
              <Button asChild variant="link" size="inline" className="min-h-11">
                <Link to={`${basePath}/alumnos/${application.studentId}`}>Ver legajo del alumno</Link>
              </Button>
            </div>
            <dl className="grid grid-cols-1 gap-3.5 sm:grid-cols-2">
              <DetailTerm label="Alumno">{application.studentFullName}</DetailTerm>
              <DetailTerm label="DNI">{application.studentDni}</DetailTerm>
              <DetailTerm label="Email">{application.studentEmail}</DetailTerm>
              <DetailTerm label="Carrera">{application.programName}</DetailTerm>
              <DetailTerm label="Cohorte">{cohort}</DetailTerm>
              <DetailTerm label="Solicitada">
                {`${formatearFecha(application.requestedAt) ?? '—'}${age ? ` · ${age}` : ''}`}
              </DetailTerm>
            </dl>
          </GlassCard>
          <DocumentationCard application={application} />
        </div>
        <div className="min-w-0">
          {isPending ? (
            <BeforeApprovingCard check={check} application={application} basePath={basePath} />
          ) : (
            <ResolutionCard application={application} />
          )}
        </div>
      </div>

      {showActions && (
        <StickyActionBar
          summary="Decisión"
          hint={
            <span id="approve-hint">
              {blockReason ?? 'Aprobar no se puede deshacer.'}
            </span>
          }
        >
          <Button
            type="button"
            variant="primary"
            className="min-h-11 px-5 aria-disabled:cursor-not-allowed aria-disabled:opacity-55"
            aria-disabled={blockReason ? 'true' : undefined}
            aria-describedby="approve-hint"
            disabled={busy}
            onClick={() => {
              if (blockReason) return;
              setApproveOpen(true);
            }}
          >
            Aprobar solicitud
          </Button>
          <Button type="button" variant="destructive" className="min-h-11 px-5 font-bold" disabled={busy} onClick={() => setRejectOpen(true)}>
            Rechazar
          </Button>
        </StickyActionBar>
      )}

      <ConfirmActionDialog
        open={approveOpen}
        onOpenChange={setApproveOpen}
        title={`¿Aprobar la solicitud de ${application.studentFullName ?? 'esta persona'}?`}
        description="Esta acción no se puede deshacer. Al confirmar:"
        confirmLabel="Aprobar"
        confirmVariant="primary"
        pending={approve.isPending}
        onConfirm={handleApprove}
      >
        <ul className="flex list-disc flex-col gap-1.5 pl-5 text-sm text-ink">
          <li>Se genera la factura de matrícula de ingreso de {application.programName}.</li>
          <li>Obtiene acceso como alumno y ve su carrera en “Mis carreras”.</li>
          <li>Recibe un aviso de la aprobación.</li>
        </ul>
        {nextId && (
          <div className="flex items-center gap-2 text-sm text-ink">
            <Checkbox
              id="open-next-application"
              checked={openNext}
              onCheckedChange={(checked) => setOpenNext(checked === true)}
            />
            <Label htmlFor="open-next-application">Abrir la siguiente solicitud pendiente</Label>
          </div>
        )}
      </ConfirmActionDialog>

      <ModalMotivo
        open={rejectOpen}
        onClose={() => setRejectOpen(false)}
        confirmando={reject.isPending}
        requerido
        minLength={MIN_REJECTION_REASON_LENGTH}
        showCounter
        titulo="Rechazar solicitud"
        descripcion="La persona va a ver este motivo en “Mis solicitudes” y podrá volver a solicitar. Es obligatorio."
        confirmarVariant="destructive"
        onConfirmar={handleReject}
      />
    </PageContainer>
  );
}
