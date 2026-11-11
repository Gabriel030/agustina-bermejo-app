import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { StickyActionBar } from '@/components/StickyActionBar';
import { ModalMotivo } from '@/components/ModalMotivo';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { UnavailableRequestState } from '@/components/director/UnavailableRequestState';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
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
  isApplicationAlreadyResolved,
  missingDocumentsText,
  nextPendingId,
} from '@/features/enrollment/lib/applicationReview';
import { formatMoney } from '@/lib/billing';
import { formatearFecha } from '@/lib/comisiones';
import { ageLabel, MIN_REJECTION_REASON_LENGTH } from '@/lib/aprobaciones';

function ReadOnlyField({ id, label, value }) {
  return (
    <div className="flex flex-col gap-1">
      <Label htmlFor={id} className="text-[12px] text-ink-faint">
        {label}
      </Label>
      <Input id={id} readOnly value={value ?? '—'} />
    </div>
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
    <GlassCard className="flex flex-col gap-2 p-4">
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="font-heading text-base font-semibold text-ink">Documentación</h2>
        {documentation && (
          <StatusPill tone={documentation.complete ? 'success' : 'warning'}>
            {documentation.complete ? 'Completa' : 'Incompleta'}
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
                <b className="block text-[14px] text-ink">{label}</b>
                <small className="text-[12px] text-ink-faint">
                  {document ? `Subido el ${formatearFecha(document.uploadedAt) ?? '—'}` : 'Todavía no lo subió'}
                </small>
              </div>
              {document ? (
                <>
                  <StatusPill tone="success">Cargado</StatusPill>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
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
        className={`grid size-[22px] shrink-0 place-items-center rounded-full text-[12px] font-bold ${ok ? 'bg-success-bg text-success-ink' : 'bg-danger-bg text-danger-ink'}`}
      >
        {ok ? '✓' : '!'}
      </span>
      <div className="flex flex-col items-start gap-1">
        <b className="text-[14px] text-ink">
          {title}
          <span className="sr-only">{ok ? ': en regla' : ': bloquea la aprobación'}</span>
        </b>
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
        <span role="alert" className="text-[13.5px] text-ink-soft">
          No pudimos verificar la documentación y la deuda del alumno.
        </span>
        <Button type="button" variant="outline" size="sm" onClick={() => check.refetch()}>
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
    <GlassCard className="flex flex-col gap-1 p-4">
      <h2 className="font-heading text-base font-semibold text-ink">Antes de aprobar</h2>
      <CheckRow ok={documentationOk} title="Documentación obligatoria">
        <p className="text-[12.5px] text-ink-faint">
          {documentationOk
            ? 'DNI (frente y dorso) y analítico cargados.'
            : `Falta ${missingDocumentsText(data.documentation?.missingTypes)}. Hasta que la suba no se puede aprobar.`}
        </p>
        {!documentationOk && (
          <Button asChild variant="link" size="inline">
            <Link to={studentPath}>Cargarlo en mostrador desde el legajo</Link>
          </Button>
        )}
      </CheckRow>
      {blockingReasonMessages(data).length > 0 && (
        <CheckRow ok={false} title="Estado de la solicitud y del alumno">
          {blockingReasonMessages(data).map((message) => (
            <p key={message} className="text-[12.5px] text-ink-faint">
              {message}
            </p>
          ))}
        </CheckRow>
      )}
      <CheckRow ok={!debtBlocks} title="Deuda del alumno">
        {debtBlocks ? (
          <>
            {debtMessages(data.debt).map((message) => (
              <p key={message} className="text-[12.5px] text-ink-faint">
                {message}
              </p>
            ))}
            <p className="text-[12.5px] text-ink-faint">Tiene que regularizarla antes de que se apruebe el ingreso.</p>
            {basePath === '/administrativo' && (
              <Button asChild variant="link" size="inline">
                <Link to={`${studentPath}?view=financiero`}>Ir al Financiero del alumno</Link>
              </Button>
            )}
          </>
        ) : (
          <p className="text-[12.5px] text-ink-faint">Sin deuda que bloquee la inscripción.</p>
        )}
      </CheckRow>
    </GlassCard>
  );
}

function ResolutionCard({ application }) {
  const invoice = application.admissionInvoice;
  return (
    <GlassCard className="flex flex-col gap-3 p-4">
      <h2 className="font-heading text-base font-semibold text-ink">Resolución</h2>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <ReadOnlyField id="resolved-by" label="Resuelta por" value={application.decidedByName} />
        <ReadOnlyField id="resolved-at" label="Fecha" value={formatearFecha(application.decidedAt)} />
      </div>
      {application.status === 'REJECTED' && application.rejectionReason && (
        <div className="rounded-2xl bg-danger-bg px-4 py-3 text-[13.5px] font-medium text-danger-ink">
          <b>Motivo del rechazo (lo ve el alumno):</b> {application.rejectionReason}
        </div>
      )}
      {application.status === 'APPROVED' && invoice && (
        <div className="rounded-2xl bg-success-bg px-4 py-3 text-[13.5px] font-medium text-success-ink">
          Matrícula de ingreso · {formatMoney(invoice.total)}
          {invoice.dueDate ? ` · vence ${formatearFecha(invoice.dueDate)}` : ''}
        </div>
      )}
      {application.status === 'EXPIRED' && (
        <div className="rounded-2xl bg-neutral-bg px-4 py-3 text-[13.5px] font-medium text-neutral-ink">
          La matrícula de ingreso no se pagó a tiempo: la aprobación venció.
        </div>
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
  const [conflict, setConflict] = useState(false);

  const advanced = location.state?.advanced === true;
  const loadedId = application?.id;
  useEffect(() => {
    if (advanced && loadedId) headingRef.current?.focus();
  }, [advanced, loadedId]);

  useEffect(() => {
    setConflict(false);
  }, [id]);

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
  const age = ageLabel(application.requestedAt);

  function handleConflict() {
    setConflict(true);
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
      <Link to={listPath} className="flex w-fit items-center gap-1.5 text-[13px] font-semibold text-ink-soft hover:text-ink">
        ← Volver a Solicitudes de ingreso
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 ref={headingRef} tabIndex={-1} className="font-heading text-2xl font-bold text-ink outline-none">
            {application.studentFullName ?? 'Solicitud de ingreso'}
          </h1>
          <p className="mt-0.5 text-sm text-ink-soft">
            {application.programName}
            {cohort ? ` · ${cohort}` : ''}
          </p>
        </div>
        <StatusPill tone={status.tone}>{status.label}</StatusPill>
      </div>

      <div role="status" aria-live="polite">
        {conflict && (
          <div className="flex flex-wrap items-center gap-3 rounded-2xl bg-warning-bg px-4 py-3 text-[13.5px] font-medium text-warning-ink">
            <span>
              <b>Otra persona ya resolvió esta solicitud.</b> No se hizo ningún cambio.
            </span>
            <Button asChild variant="outline" size="sm">
              <Link to={listPath}>Volver a la bandeja</Link>
            </Button>
          </div>
        )}
      </div>

      <GlassCard className="flex flex-col gap-3 p-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <ReadOnlyField id="student-name" label="Alumno" value={application.studentFullName} />
          <ReadOnlyField id="student-dni" label="DNI" value={application.studentDni} />
          <ReadOnlyField id="student-email" label="Email" value={application.studentEmail} />
          <ReadOnlyField id="program-name" label="Carrera" value={application.programName} />
          <ReadOnlyField id="cohort-name" label="Cohorte" value={cohort} />
          <ReadOnlyField
            id="requested-at"
            label="Solicitada"
            value={`${formatearFecha(application.requestedAt) ?? '—'}${age ? ` · ${age}` : ''}`}
          />
        </div>
        <Button asChild variant="link" size="inline" className="w-fit">
          <Link to={`${basePath}/alumnos/${application.studentId}`}>Ver legajo del alumno</Link>
        </Button>
      </GlassCard>

      <DocumentationCard application={application} />

      {isPending ? (
        <BeforeApprovingCard check={check} application={application} basePath={basePath} />
      ) : (
        <ResolutionCard application={application} />
      )}

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
            variant="gradient"
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
          <Button type="button" variant="destructive" disabled={busy} onClick={() => setRejectOpen(true)}>
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
        confirmVariant="gradient"
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
