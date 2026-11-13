import { Download, Link2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Button } from '@/components/ui/button';
import { formatearFechaCorta } from '@/lib/avisos';
import {
  useCancelRegularCertificateRequest,
  useDownloadRegularCertificatePdf,
  useRegularCertificateState,
  useRequestRegularCertificate,
} from '../hooks/useRegularCertificate';
import {
  REGULAR_VIEW,
  REGULAR_VIEW_PILL,
  canRequestRegular,
  deriveRegularView,
  pastCertificates,
  regularDownloadErrorMessage,
  regularRequestErrorMessage,
} from '../lib/regularCertificateState';
import { saveBlob } from '../lib/saveBlob';
import { CodeWithCopy } from './CodeWithCopy';

function summaryFor(view, state) {
  switch (view) {
    case REGULAR_VIEW.AVAILABLE:
      return state.staffConfirmationRequired
        ? 'Podés pedirla ahora. Administración la confirma y te avisamos cuando esté lista. Vale 30 días y se verifica con un código.'
        : 'Podés pedirla ahora. Vale 30 días y se verifica con un código.';
    case REGULAR_VIEW.PENDING:
      return `Pedida el ${formatearFechaCorta(state.pendingRequest?.requestedAt)}. Administración tiene que confirmarla. Cuando esté lista te llega un aviso.`;
    case REGULAR_VIEW.EXPIRED:
      return 'Ya no acredita que seas alumno regular. Pedí una nueva: la anterior queda en el historial.';
    case REGULAR_VIEW.UNAVAILABLE_ATTENDANCE:
      return 'Estás libre en todas tus materias por inasistencias, así que no figurás como alumno regular.';
    case REGULAR_VIEW.UNAVAILABLE_PAYMENTS:
      return 'Tenés pagos pendientes. Cuando estés al día podés volver a pedirla.';
    case REGULAR_VIEW.UNAVAILABLE_REJECTED:
      return `Administración no pudo confirmar tu pedido: «${state.rejectionReason ?? 'sin motivo informado'}». Cuando lo resuelvas podés pedirla de nuevo.`;
    case REGULAR_VIEW.UNAVAILABLE_ENROLLMENT:
      return 'No tenés una inscripción activa en esta carrera.';
    default:
      return null;
  }
}

function pillFor(view, state) {
  const pill = REGULAR_VIEW_PILL[view];
  if (view !== REGULAR_VIEW.EXPIRED) return pill;
  return { ...pill, label: `Venció el ${formatearFechaCorta(pastCertificates(state)[0].expiresAt)}` };
}

function PastCertificates({ state }) {
  const past = pastCertificates(state);
  if (past.length === 0) return null;
  return (
    <GlassCard className="flex flex-col gap-2 p-4">
      <h3 className="font-heading text-base font-semibold text-ink">Historial</h3>
      <ul className="flex flex-col gap-1 text-sm text-ink-soft">
        {past.map((certificate) => (
          <li key={certificate.id} className="flex flex-wrap justify-between gap-x-3 rounded-lg px-3 py-2">
            <span>Emitida el {formatearFechaCorta(certificate.issuedAt)}</span>
            <span>
              {certificate.status === 'REVOKED' ? 'Revocada' : `Venció el ${formatearFechaCorta(certificate.expiresAt)}`} · sin
              descarga
            </span>
          </li>
        ))}
      </ul>
      <p className="text-size-13 text-ink-faint">Las constancias descargadas antes no tienen código de verificación.</p>
    </GlassCard>
  );
}

function ValidDetails({ certificate, onDownload, downloading }) {
  async function copyLink() {
    try {
      await navigator.clipboard.writeText(certificate.verificationUrl);
      toast.success('Enlace copiado');
    } catch {
      toast.error('No pudimos copiar el enlace.');
    }
  }

  return (
    <>
      <CodeWithCopy code={certificate.code} />
      <div className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
        <div>
          <div className="text-size-13 text-ink-faint">Emitida</div>
          <div className="font-semibold text-ink">{formatearFechaCorta(certificate.issuedAt)}</div>
        </div>
        <div>
          <div className="text-size-13 text-ink-faint">Vence</div>
          <div className="font-semibold text-ink">{formatearFechaCorta(certificate.expiresAt)}</div>
        </div>
      </div>
      <div className="flex flex-col gap-2 sm:flex-row">
        <Button variant="gradient" className="w-full sm:w-auto" loading={downloading} onClick={() => onDownload(certificate.id)}>
          <Download aria-hidden="true" />
          Descargar PDF
        </Button>
        <Button variant="outline" className="w-full sm:w-auto" onClick={copyLink}>
          <Link2 aria-hidden="true" />
          Copiar enlace de verificación
        </Button>
      </div>
    </>
  );
}

export function RegularCertificateCard({ carreraId }) {
  const { data: state, isLoading, isError, error, refetch } = useRegularCertificateState(carreraId);
  const requestCertificate = useRequestRegularCertificate(carreraId);
  const cancelRequest = useCancelRegularCertificateRequest(carreraId);
  const downloadPdf = useDownloadRegularCertificatePdf(carreraId);

  if (isLoading) return <Skeleton className="h-40" />;
  if (isError || !state) return <LoadErrorState error={error} onRetry={refetch} resource="tu constancia de alumno regular" />;

  const view = deriveRegularView(state);
  const pill = pillFor(view, state);
  const summary = summaryFor(view, state);

  async function request() {
    try {
      const result = await requestCertificate.mutateAsync();
      toast.success(result?.certificate ? 'Constancia emitida' : 'Pedido enviado. Te avisamos cuando esté lista.');
    } catch (requestError) {
      toast.error(regularRequestErrorMessage(requestError));
    }
  }

  async function cancel() {
    try {
      await cancelRequest.mutateAsync(state.pendingRequest.id);
      toast.success('Pedido cancelado');
    } catch (cancelError) {
      toast.error(cancelError?.message ?? 'No pudimos cancelar el pedido. Probá de nuevo.');
    }
  }

  async function download(certificateId) {
    try {
      const { blob, filename } = await downloadPdf.mutateAsync(certificateId);
      saveBlob(blob, filename);
    } catch (downloadError) {
      toast.error(regularDownloadErrorMessage(downloadError));
    }
  }

  return (
    <>
      <GlassCard className="flex flex-col gap-3 p-4">
        <div className="flex flex-wrap items-center justify-between gap-2.5">
          <h2 className="font-heading text-base font-semibold text-ink">Alumno regular</h2>
          <StatusPill tone={pill.tone}>{pill.label}</StatusPill>
        </div>
        {view === REGULAR_VIEW.VALID && (
          <ValidDetails certificate={state.certificate} onDownload={download} downloading={downloadPdf.isPending} />
        )}
        {summary && <p className="text-sm text-ink-soft">{summary}</p>}
        {canRequestRegular(view) && (
          <div>
            <Button variant="gradient" className="w-full sm:w-auto" loading={requestCertificate.isPending} onClick={request}>
              {view === REGULAR_VIEW.EXPIRED ? 'Pedir una nueva' : 'Pedir constancia'}
            </Button>
          </div>
        )}
        {view === REGULAR_VIEW.PENDING && (
          <div>
            <Button variant="text" size="inline" loading={cancelRequest.isPending} onClick={cancel}>
              Cancelar pedido
            </Button>
          </div>
        )}
        {view === REGULAR_VIEW.UNAVAILABLE_ATTENDANCE && (
          <div>
            <Button asChild variant="outline" className="w-full sm:w-auto">
              <Link to={`/alumno/mis-carreras?carrera=${carreraId}&tab=asistencia`}>Ver mi asistencia</Link>
            </Button>
          </div>
        )}
        {view === REGULAR_VIEW.UNAVAILABLE_PAYMENTS && (
          <div>
            <Button asChild variant="outline" className="w-full sm:w-auto">
              <Link to="/alumno/pagos">Ver mis pagos</Link>
            </Button>
          </div>
        )}
      </GlassCard>
      <PastCertificates state={state} />
    </>
  );
}
