import { useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { LoadErrorState } from '@/components/LoadErrorState';
import { EstadoSolicitudBadge } from '@/features/enrollment/components/EstadoSolicitudBadge';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { apiFetch } from '@/api/client';
import { useMisSolicitudesCarrera, useMyDocuments } from '@/features/enrollment/hooks/useCatalogo';
import { paymentOriginState } from '@/hooks/usePaymentReturn';
import { CircleCheck, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Stepper } from '@/components/Stepper';
import { applicationProgress } from '@/lib/applicationProgress';
import { DOCUMENT_ACCEPT, documentUploadErrorMessage, validateDocumentFile } from '@/lib/applicationDocumentUpload';
import { formatFecha, formatMoney } from '@/lib/utils';

const TIPOS_DOCUMENTO = [
  { type: 'DNI_FRENTE', label: 'DNI — frente' },
  { type: 'DNI_DORSO', label: 'DNI — dorso' },
  { type: 'ANALITICO_SECUNDARIO', label: 'Analítico del secundario' },
];

const MY_APPLICATIONS_PATH = '/catalogo?tab=mis-solicitudes';

function documentStatusText({ fileName, previousUploadDate, documentsFailed }) {
  if (fileName) return `${fileName} · subido`;
  if (previousUploadDate) return `Subido el ${formatFecha(previousUploadDate)}`;
  if (documentsFailed) return 'No pudimos verificar si ya lo subiste';
  return 'Sin subir todavía';
}

export function DetalleSolicitud() {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const originState = paymentOriginState(`/mis-solicitudes/${id}`, 'Volver a tu solicitud');
  const applications = useMisSolicitudesCarrera();
  const documents = useMyDocuments();
  const [subidosEnSesion, setSubidosEnSesion] = useState({});
  const [subiendo, setSubiendo] = useState(null);
  const [errorSubida, setErrorSubida] = useState(null);
  const [uploadAnnouncement, setUploadAnnouncement] = useState('');
  const [replaceTarget, setReplaceTarget] = useState(null);
  const fileInputRefs = useRef({});

  const solicitud = applications.data?.find((application) => String(application.id) === id) ?? null;
  const previousDocuments = Object.fromEntries((documents.data ?? []).map((studentDocument) => [studentDocument.type, studentDocument.uploadedAt]));

  function elegirArchivo(type) {
    fileInputRefs.current[type]?.click();
  }

  function requestFile(type, label, uploaded) {
    if (uploaded) {
      setReplaceTarget({ type, label });
      return;
    }
    elegirArchivo(type);
  }

  function confirmReplace() {
    const { type } = replaceTarget;
    setReplaceTarget(null);
    setTimeout(() => elegirArchivo(type), 0);
  }

  async function subirArchivo(type, label, input) {
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    setUploadAnnouncement('');
    const validationMessage = validateDocumentFile(file);
    if (validationMessage) {
      setErrorSubida(validationMessage);
      return;
    }
    setSubiendo(type);
    setErrorSubida(null);
    try {
      const formData = new FormData();
      formData.append('file', file);
      await apiFetch(`/me/program-enrollment-applications/${id}/documents?type=${type}`, {
        method: 'POST',
        body: formData,
      });
      setSubidosEnSesion((prev) => ({ ...prev, [type]: file.name }));
      setUploadAnnouncement(`${label}: ${file.name} subido`);
      queryClient.invalidateQueries({ queryKey: ['me-documents'] });
    } catch (error) {
      setErrorSubida(documentUploadErrorMessage(error));
    } finally {
      setSubiendo(null);
    }
  }

  if (applications.isError) {
    return (
      <PageContainer>
        <LoadErrorState
          error={applications.error}
          onRetry={() => applications.refetch()}
          resource="la solicitud"
          backTo={MY_APPLICATIONS_PATH}
          backLabel="Mis solicitudes"
        />
      </PageContainer>
    );
  }

  if (applications.isSuccess && !solicitud) {
    return (
      <PageContainer>
        <GlassCard className="flex flex-1 flex-col items-center justify-center gap-2 p-12 text-center">
          <h1 className="font-heading text-xl font-semibold text-ink">Solicitud no encontrada</h1>
          <Link to={MY_APPLICATIONS_PATH} className="text-sm font-semibold text-primary">
            Volver a Mis solicitudes
          </Link>
        </GlassCard>
      </PageContainer>
    );
  }

  if (!solicitud) {
    return (
      <PageContainer>
        <Skeleton className="h-9 w-72" />
        <Skeleton className="h-40" />
        <Skeleton className="h-40" />
      </PageContainer>
    );
  }

  const invoice = solicitud.admissionInvoice;
  const rejectionText = solicitud.rejectionReason?.trim() || 'No se indicó un motivo; consultá con Secretaría.';

  return (
    <PageContainer
      title={solicitud.programName}
      description={`Solicitada el ${formatFecha(solicitud.requestedAt)}`}
      action={<EstadoSolicitudBadge solicitud={solicitud} />}
    >
      <Link to={MY_APPLICATIONS_PATH} className="-mt-3 flex w-fit items-center gap-1.5 text-[13px] font-semibold text-ink-soft hover:text-ink">
        <span aria-hidden="true">←</span> Mis solicitudes
      </Link>

      <Stepper {...applicationProgress(solicitud)} />

      {solicitud.status === 'REJECTED' && (
        <GlassCard className="flex flex-col gap-3 bg-danger-bg p-5 text-[13px] text-danger">
          <div>
            <span className="font-bold">Solicitud rechazada.</span> {rejectionText}
          </div>
          <div>Podés volver a solicitar el ingreso a {solicitud.programName} cuando corrijas lo indicado.</div>
          <Button variant="outline" className="w-fit" onClick={() => navigate(`/catalogo/carreras/${solicitud.programId}`)}>
            Solicitar de nuevo
          </Button>
        </GlassCard>
      )}

      {solicitud.status === 'EXPIRED' && (
        <GlassCard className="flex flex-col gap-3 bg-danger-bg p-5 text-[13px] text-danger">
          <div>
            <span className="font-bold">Solicitud vencida.</span> Cerró la admisión sin que se pagara la matrícula de ingreso y la factura quedó anulada.
          </div>
          <div>Podés volver a solicitar el ingreso a {solicitud.programName} en la próxima admisión.</div>
          <Button variant="outline" className="w-fit" onClick={() => navigate(`/catalogo/carreras/${solicitud.programId}`)}>
            Solicitar de nuevo
          </Button>
        </GlassCard>
      )}

      {solicitud.status === 'APPROVED' && !invoice && (
        <GlassCard className="bg-neutral-bg p-5 text-[13px] text-ink-soft">
          <span className="font-bold text-ink">Solicitud aprobada.</span> Consultá con Secretaría el pago de la matrícula de ingreso.
        </GlassCard>
      )}

      {solicitud.status === 'APPROVED' && invoice?.paymentStatus === 'PAID' && solicitud.programEnrollmentId && (
        <GlassCard className="bg-success-bg p-5 text-[13px] text-success">
          <span className="font-bold">Solicitud aprobada.</span> Pagaste la matrícula de ingreso: tu inscripción a la carrera está confirmada.
        </GlassCard>
      )}

      {solicitud.status === 'APPROVED' && invoice?.paymentStatus === 'PAID' && !solicitud.programEnrollmentId && (
        <GlassCard className="bg-warning-bg p-5 text-[13px] text-warning">
          <span className="font-bold">Solicitud aprobada.</span> Pagaste la matrícula de ingreso. Estamos confirmando tu inscripción en {solicitud.programName}.
        </GlassCard>
      )}

      {solicitud.status === 'APPROVED' && invoice && invoice.paymentStatus !== 'PAID' && (
        <GlassCard className="flex flex-col gap-3 p-5">
          <div className="text-[13px] text-ink-soft">
            <span className="font-bold text-ink">Solicitud aprobada.</span> Cuando confirmemos el pago, tu inscripción a la carrera queda confirmada.
          </div>
          {invoice.paymentStatus === 'OVERDUE' && (
            <div className="rounded-xl bg-warning-bg px-4 py-3 text-[13px] text-warning">
              <span className="font-bold">La matrícula está vencida.</span> Pagala antes de que cierre la admisión: si cierra sin el pago, la solicitud
              vence y la factura se anula.
            </div>
          )}
          <div className="flex items-center justify-between rounded-xl bg-neutral-bg px-4 py-3">
            <div>
              <div className="text-xs text-ink-faint">Matrícula de ingreso</div>
              <div className="font-heading text-lg font-bold text-ink">{formatMoney(invoice.total)}</div>
              <div className="text-xs text-ink-faint">
                {invoice.paymentStatus === 'OVERDUE' ? 'Venció' : 'Vence'} el {formatFecha(invoice.dueDate)}
              </div>
            </div>
            {invoice.receiptStatus === 'PENDING_REVIEW' ? (
              <div className="flex flex-col items-end gap-1.5">
                <StatusPill tone="warning">Pago en revisión</StatusPill>
                <Button
                  variant="text"
                  size="inline"
                  type="button"
                  onClick={() => navigate(`/alumno/pagos/${invoice.id}/transferencia`, { state: originState })}
                  className="text-[12.5px] font-semibold"
                  >
                  Ver estado del pago
                </Button>
              </div>
            ) : (
              <Button variant="gradient" onClick={() => navigate(`/alumno/pagos/${invoice.id}/seleccion`, { state: originState })}>
                Pagar matrícula
              </Button>
            )}
          </div>
          {invoice.receiptStatus === 'PENDING_REVIEW' && (
            <div className="text-xs text-ink-faint">
              Subiste el comprobante el {formatFecha(invoice.receiptUploadedAt)}; cuando lo aprobemos tu inscripción queda confirmada.
            </div>
          )}
        </GlassCard>
      )}

      {solicitud.status !== 'REJECTED' && solicitud.status !== 'EXPIRED' && solicitud.status !== 'APPROVED' && (
        <>
          <GlassCard className="p-5 text-[13px] text-ink-soft">
            Tu solicitud está en revisión. No se te cobró nada: si la aprueban, vas a recibir la factura de la matrícula de ingreso y vas
            a tener un plazo para pagarla. Te avisamos por notificación cuando haya novedades.
          </GlassCard>

          <h2 className="font-heading text-[15px] font-semibold text-ink-soft">Documentos de tu solicitud</h2>

          {documents.isError && (
            <div role="alert" className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-warning-bg px-3 py-2 text-xs font-medium text-warning">
              No pudimos ver los documentos que ya subiste.
              <Button type="button" variant="outline" size="sm" onClick={() => documents.refetch()}>
                Reintentar
              </Button>
            </div>
          )}

          {errorSubida && (
            <div role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">
              {errorSubida}
            </div>
          )}

          <div role="status" className="sr-only">
            {uploadAnnouncement}
          </div>

          <GlassCard className="flex flex-col p-1">
            {TIPOS_DOCUMENTO.map(({ type, label }) => {
              const fileName = subidosEnSesion[type];
              const previousUploadDate = previousDocuments[type];
              const uploaded = Boolean(fileName || previousUploadDate);
              return (
                <div key={type} className="flex items-center justify-between border-b border-border px-4 py-4 last:border-b-0">
                  <div className="flex items-center gap-3.5">
                    <div
                      className={`flex h-9 w-9 items-center justify-center rounded-xl ${uploaded ? 'bg-success-bg' : 'bg-neutral-bg'}`}
                    >
                      {uploaded ? (
                        <CircleCheck size={18} className="text-success" aria-hidden="true" />
                      ) : (
                        <Plus size={18} className="text-ink-faint" aria-hidden="true" />
                      )}
                    </div>
                    <div>
                      <div className="text-[13.5px] font-bold text-ink">{label}</div>
                      <div className={`text-xs ${uploaded ? 'text-success' : 'text-ink-faint'}`}>
                        {documentStatusText({ fileName, previousUploadDate, documentsFailed: documents.isError })}
                      </div>
                    </div>
                  </div>
                  <input
                    ref={(el) => (fileInputRefs.current[type] = el)}
                    type="file"
                    accept={DOCUMENT_ACCEPT}
                    aria-label={`Archivo de ${label}`}
                    className="hidden"
                    onChange={(e) => subirArchivo(type, label, e.target)}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    loading={subiendo === type}
                    aria-label={`${uploaded ? 'Reemplazar' : 'Subir archivo de'} ${label}`}
                    onClick={() => requestFile(type, label, uploaded)}
                  >
                    {uploaded ? 'Reemplazar' : 'Subir archivo'}
                  </Button>
                </div>
              );
            })}
          </GlassCard>
        </>
      )}

      <Dialog open={Boolean(replaceTarget)} onOpenChange={(open) => !open && setReplaceTarget(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>¿Reemplazar {replaceTarget?.label}?</DialogTitle>
            <DialogDescription>
              Este archivo se guarda en tu legajo y reemplaza al anterior en todas tus solicitudes.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setReplaceTarget(null)}>
              Cancelar
            </Button>
            <Button type="button" onClick={confirmReplace}>
              Elegir archivo nuevo
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
