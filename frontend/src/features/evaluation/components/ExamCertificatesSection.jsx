import { useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Notice } from '@/components/Notice';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { ModalMotivo } from '@/components/ModalMotivo';
import { describeError } from '@/api/describeError';
import { saveBlob } from '@/features/graduation/certificates/lib/saveBlob';
import {
  certificateCaps,
  downloadErrorCode,
  isSelectable,
  issueTitle,
  issuedHeadline,
  displayName,
  summarizeBatch,
} from '@/features/evaluation/lib/examCertificates';
import {
  useDownloadExamCertificatePdf,
  useExamCertificateCandidates,
  useIssueExamCertificateBatch,
  useRevokeExamCertificate,
} from '@/features/evaluation/hooks/useExamCertificates';
import { ExamCertificatesTable } from '@/features/evaluation/components/ExamCertificatesTable';

function ResultNotice({ result, onDismiss, noticeRef }) {
  const dismiss = (
    <Button type="button" variant="outline" size="sm" className="min-h-11 shrink-0 md:min-h-8" onClick={onDismiss}>
      Cerrar
    </Button>
  );
  if (result.kind === 'failed') {
    return (
      <Notice ref={noticeRef} tabIndex={-1} tone="danger" role="note" className="flex flex-wrap items-start justify-between gap-3">
        <span>
          <b className="font-bold">No se emitió nada.</b> {result.message} Podés reintentar con la misma selección.
        </span>
        {dismiss}
      </Notice>
    );
  }
  if (result.kind === 'revoked') {
    return (
      <Notice ref={noticeRef} tabIndex={-1} tone="success" role="note" className="flex flex-wrap items-start justify-between gap-3">
        <span>
          <b className="font-bold">Constancia revocada.</b> {displayName(result.studentName)} puede recibir una nueva en el próximo lote.
        </span>
        {dismiss}
      </Notice>
    );
  }
  return (
    <Notice ref={noticeRef} tabIndex={-1} tone={result.omitted.length ? 'warning' : 'success'} role="note" className="flex flex-wrap items-start justify-between gap-3">
      <div className="flex flex-col gap-0.5">
        <b className="font-bold">{issuedHeadline(result)}</b>
        {result.omitted.map((item) => (
          <span key={item.registrationId}>
            {item.studentName}: {item.reason}
          </span>
        ))}
      </div>
      {dismiss}
    </Notice>
  );
}

export function ExamCertificatesSection({ mesaId, role, onBack }) {
  const caps = certificateCaps(role);
  const candidatesQuery = useExamCertificateCandidates(mesaId);
  const issueBatch = useIssueExamCertificateBatch(mesaId);
  const revoke = useRevokeExamCertificate(mesaId);
  const download = useDownloadExamCertificatePdf();
  const [downloadingId, setDownloadingId] = useState(null);
  const [selected, setSelected] = useState(() => new Set());
  const [confirming, setConfirming] = useState(false);
  const [result, setResult] = useState(null);
  const [revoking, setRevoking] = useState(null);
  const [revokeError, setRevokeError] = useState('');
  const resultRef = useRef(null);

  useEffect(() => {
    if (!result) return undefined;
    const timer = setTimeout(() => resultRef.current?.focus(), 0);
    return () => clearTimeout(timer);
  }, [result]);

  const candidates = candidatesQuery.data ?? [];
  const eligible = candidates.filter(isSelectable);
  const selectedIds = eligible.filter((candidate) => selected.has(candidate.registrationId)).map((candidate) => candidate.registrationId);
  const allSelected = eligible.length > 0 && selectedIds.length === eligible.length;

  function toggle(registrationId, checked) {
    setSelected((current) => {
      const next = new Set(current);
      if (checked) next.add(registrationId);
      else next.delete(registrationId);
      return next;
    });
  }

  function toggleAll() {
    setSelected(allSelected ? new Set() : new Set(eligible.map((candidate) => candidate.registrationId)));
  }

  async function confirmIssue() {
    try {
      const response = await issueBatch.mutateAsync(selectedIds);
      setResult({ kind: 'issued', ...summarizeBatch(response, candidates) });
      setSelected(new Set());
    } catch (error) {
      setResult({ kind: 'failed', message: describeError(error).message });
    } finally {
      setConfirming(false);
    }
  }

  async function confirmRevoke(reason) {
    try {
      await revoke.mutateAsync({ certificateId: revoking.certificateId, reason });
      setResult({ kind: 'revoked', studentName: revoking.studentName });
      setRevoking(null);
      setRevokeError('');
      return true;
    } catch (error) {
      setRevokeError(describeError(error).message);
      return false;
    }
  }

  async function downloadPdf(candidate) {
    setDownloadingId(candidate.certificateId);
    try {
      const { blob, filename } = await download.mutateAsync(candidate.certificateId);
      saveBlob(blob, filename === 'archivo' ? 'constancia-de-examen.pdf' : filename);
    } catch (error) {
      toast.error(describeError(error).message, { description: downloadErrorCode(error) ?? undefined });
    } finally {
      setDownloadingId(null);
    }
  }

  function closeRevoke() {
    setRevoking(null);
    setRevokeError('');
  }

  let content;
  if (candidatesQuery.isError && candidatesQuery.error?.status === 404) {
    content = (
      <EmptyState
        title="No encontramos esta mesa"
        description="La mesa no existe o no es una de las tuyas."
        action={
          onBack && (
            <Button type="button" variant="outline" className="min-h-11 md:min-h-8" onClick={onBack}>
              Ir a Mis mesas
            </Button>
          )
        }
      />
    );
  } else if (candidatesQuery.isError) {
    content = (
      <Notice tone="danger" role="alert" className="flex flex-wrap items-start justify-between gap-3">
        <span>No pudimos verificar quién puede recibir constancia. No se puede emitir por ahora.</span>
        <Button type="button" variant="outline" size="sm" className="min-h-11 shrink-0 md:min-h-8" onClick={() => candidatesQuery.refetch()}>
          Reintentar
        </Button>
      </Notice>
    );
  } else if (candidatesQuery.isPending) {
    content = (
      <div className="flex flex-col gap-2" aria-busy="true" aria-label="Cargando constancias">
        <Skeleton className="h-12" />
        <Skeleton className="h-12" />
        <Skeleton className="h-12" />
      </div>
    );
  } else if (candidates.length === 0) {
    content = (
      <EmptyState
        title="Todavía no hay presentes"
        description="Cuando registres la presencia de los alumnos, vas a poder emitir sus constancias de examen."
      />
    );
  } else {
    content = (
      <>
        {eligible.length > 0 && (
          <label className="flex min-h-11 w-fit cursor-pointer items-center gap-3 text-size-13 font-semibold text-ink-soft md:min-h-8">
            <Checkbox
              checked={allSelected}
              disabled={issueBatch.isPending}
              onCheckedChange={toggleAll}
              aria-label={`Seleccionar todos los elegibles (${eligible.length})`}
            />
            Seleccionar todos los elegibles ({eligible.length})
          </label>
        )}
        <ExamCertificatesTable
          candidates={candidates}
          selected={selected}
          busy={issueBatch.isPending}
          canRevoke={caps.revokes}
          downloadingId={downloadingId}
          onToggle={toggle}
          onRevoke={setRevoking}
          onDownload={downloadPdf}
        />
      </>
    );
  }

  const showActions = !candidatesQuery.isError && !candidatesQuery.isPending && candidates.length > 0;

  return (
    <GlassCard className="flex flex-col gap-3 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="font-heading text-size-16 font-semibold text-ink">Constancias de examen</h2>
          <p className="text-size-13 text-ink-soft">Para quienes rindieron. Cada alumno la descarga desde su panel.</p>
        </div>
        {showActions && (
          <div className="flex flex-col items-end gap-1">
            <Button
              type="button"
              variant="primary"
              className="min-h-11 md:min-h-8"
              loading={issueBatch.isPending}
              disabled={selectedIds.length === 0}
              onClick={() => setConfirming(true)}
            >
              {selectedIds.length > 0 ? `Emitir constancias (${selectedIds.length})` : 'Emitir constancias'}
            </Button>
            {selectedIds.length === 0 && (
              <span className="text-size-12 text-ink-faint">
                {eligible.length > 0 ? 'Marcá a quién emitirle la constancia.' : 'No hay alumnos para emitir.'}
              </span>
            )}
          </div>
        )}
      </div>
      <div role="status" aria-live="polite">
        {result && result.kind !== 'failed' && <ResultNotice result={result} noticeRef={resultRef} onDismiss={() => setResult(null)} />}
      </div>
      <div role="alert">
        {result?.kind === 'failed' && <ResultNotice result={result} noticeRef={resultRef} onDismiss={() => setResult(null)} />}
      </div>
      {content}
      <ConfirmActionDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={issueTitle(selectedIds.length)}
        description="Cada alumno la descarga desde su panel. Si alguien dejó de estar al día, se omite."
        confirmLabel="Emitir"
        confirmVariant="primary"
        pending={issueBatch.isPending}
        buttonClassName="min-h-11 md:min-h-8"
        onConfirm={confirmIssue}
      />
      {revoking && (
        <ModalMotivo
          open
          requerido
          titulo={`Revocar la constancia de ${displayName(revoking.studentName)}`}
          descripcion="Deja de ser válida y la verificación pública la muestra como revocada."
          placeholder="Motivo"
          confirmarLabel="Revocar constancia"
          confirmarVariant="destructive"
          mensajeRequerido="El motivo es obligatorio."
          confirmando={revoke.isPending}
          errorMessage={revokeError}
          onClose={closeRevoke}
          onConfirmar={confirmRevoke}
        />
      )}
    </GlassCard>
  );
}
