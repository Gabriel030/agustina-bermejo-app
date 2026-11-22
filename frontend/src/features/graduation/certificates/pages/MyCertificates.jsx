import { useCallback, useEffect, useRef, useState } from 'react';
import { Download, Eye } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { formatearFechaCorta } from '@/lib/avisos';
import { useOwnCertificatePdf, useOwnCertificates } from '../hooks/useOwnCertificates';
import {
  ownCertificateActionError,
  ownCertificateKindLabel,
  ownCertificatePreviewLabel,
  ownCertificateStatusPill,
} from '../lib/ownCertificates';
import { saveBlob } from '../lib/saveBlob';

function Field({ label, children }) {
  return (
    <div>
      <dt className="text-size-13 text-ink-faint">{label}</dt>
      <dd className="font-semibold text-ink">{children}</dd>
    </div>
  );
}

function OwnCertificateCard({ certificate, busyId, onPreview, onDownload }) {
  const pill = ownCertificateStatusPill(certificate.status);
  const busy = busyId === certificate.id;
  return (
    <li>
      <GlassCard className="flex flex-col gap-3 p-4">
        <div className="flex flex-wrap items-start justify-between gap-2.5">
          <div>
            <p className="text-size-13 text-ink-faint">{ownCertificateKindLabel(certificate.kind)}</p>
            <h2 className="font-heading text-base font-semibold text-ink">{certificate.titleName}</h2>
          </div>
          <StatusPill tone={pill.tone}>{pill.label}</StatusPill>
        </div>
        <dl className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-3">
          <Field label="Egreso">{formatearFechaCorta(certificate.graduationDate)}</Field>
          <Field label="Emitido">{formatearFechaCorta(certificate.issuedAt)}</Field>
          <Field label="Número de registro">{certificate.registryNumber ?? '—'}</Field>
          {certificate.revokedAt && <Field label="Fecha de revocación">{formatearFechaCorta(certificate.revokedAt)}</Field>}
        </dl>
        {(certificate.previewable || certificate.downloadable) && (
          <div className="flex flex-col gap-2 sm:flex-row">
            {certificate.downloadable && (
              <Button variant="primary" className="w-full sm:w-auto" loading={busy} onClick={() => onDownload(certificate)}>
                <Download aria-hidden="true" />
                Descargar
              </Button>
            )}
            {certificate.previewable && (
              <Button variant="outline" className="w-full sm:w-auto" disabled={busy} onClick={() => onPreview(certificate)}>
                <Eye aria-hidden="true" />
                Vista previa
              </Button>
            )}
          </div>
        )}
      </GlassCard>
    </li>
  );
}

export function MyCertificates() {
  const { data, isLoading, isError, error, refetch } = useOwnCertificates();
  const pdf = useOwnCertificatePdf();
  const [busyId, setBusyId] = useState(null);
  const [preview, setPreview] = useState(null);
  const [actionError, setActionError] = useState(null);
  const previewUrl = useRef(null);

  const revokePreviewUrl = useCallback(() => {
    if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
    previewUrl.current = null;
  }, []);

  useEffect(() => revokePreviewUrl, [revokePreviewUrl]);

  const closePreview = useCallback(() => {
    revokePreviewUrl();
    setPreview(null);
    setActionError(null);
  }, [revokePreviewUrl]);

  async function fetchPdf(certificate) {
    setBusyId(certificate.id);
    setActionError(null);
    try {
      return await pdf.mutateAsync(certificate.id);
    } catch (pdfError) {
      setActionError(ownCertificateActionError(pdfError));
      return null;
    } finally {
      setBusyId(null);
    }
  }

  async function download(certificate) {
    const result = await fetchPdf(certificate);
    if (result) saveBlob(result.blob, result.filename);
  }

  async function openPreview(certificate) {
    const result = await fetchPdf(certificate);
    if (!result) return;
    revokePreviewUrl();
    previewUrl.current = URL.createObjectURL(result.blob);
    setPreview({ url: previewUrl.current, certificate });
  }

  function retry() {
    setActionError(null);
    refetch();
  }

  let content;
  if (isLoading) {
    content = (
      <div className="flex flex-col gap-3" aria-busy="true" aria-label="Cargando certificados">
        <Skeleton className="h-40" />
        <Skeleton className="h-40" />
      </div>
    );
  } else if (isError || !data) {
    content = <LoadErrorState error={error} onRetry={retry} resource="tus certificados" />;
  } else if (data.length === 0) {
    content = (
      <GlassCard className="p-4">
        <EmptyState icon="🎓" title="Todavía no tenés certificados" description="Cuando egreses de una carrera o un curso, tu certificado aparece acá." />
      </GlassCard>
    );
  } else {
    content = (
      <ul className="flex flex-col gap-3">
        {data.map((certificate) => (
          <OwnCertificateCard
            key={certificate.id}
            certificate={certificate}
            busyId={busyId}
            onPreview={openPreview}
            onDownload={download}
          />
        ))}
      </ul>
    );
  }

  return (
    <PageContainer>
      <div>
        <h1 className="font-heading text-2xl font-bold tracking-tight text-ink">Mis certificados</h1>
        <p className="mt-1 text-size-13 text-ink-faint">Tus certificados de egreso, con su estado.</p>
      </div>
      {actionError && (
        <p role="alert" className="text-size-14 font-medium text-danger">
          {actionError}
        </p>
      )}
      {content}
      <Dialog open={Boolean(preview)} onOpenChange={(open) => !open && closePreview()}>
        <DialogContent className="sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>Vista previa</DialogTitle>
            <DialogDescription>Así se ve tu certificado de egreso.</DialogDescription>
          </DialogHeader>
          {preview && (
            <iframe
              src={preview.url}
              title={ownCertificatePreviewLabel(preview.certificate)}
              className="h-[70vh] w-full rounded-md border-0"
            />
          )}
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
