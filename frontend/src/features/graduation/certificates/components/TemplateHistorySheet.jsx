import { useState } from 'react';
import { useReturnFocus } from '@/features/graduation/certificates/hooks/useReturnFocus';
import { describeError } from '@/api/describeError';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import {
  useLoadTemplateVersion,
  useTemplateVersion,
  useTemplateVersions,
} from '@/features/graduation/certificates/hooks/useCertificateTemplate';
import { formFromContent, textFieldsOf } from '@/features/graduation/certificates/lib/certificateTemplate';
import { formatDayAndTime } from '@/features/graduation/certificates/lib/certificateView';

function formatPublishedAt(value) {
  return value ? formatDayAndTime(new Date(value)) : '';
}

function SignerLine({ label, signer }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-size-12 font-semibold text-ink-faint">{label}</dt>
      <dd className="text-size-14 text-ink">{signer?.name ? `${signer.name} · ${signer.role ?? ''}` : 'Sin firmante'}</dd>
    </div>
  );
}

function VersionDialog({ templateKey, number, onClose, onCloseAutoFocus }) {
  const version = useTemplateVersion(templateKey, number);
  const content = version.data?.content;
  return (
    <Dialog open={number != null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-2xl" onCloseAutoFocus={onCloseAutoFocus}>
        <DialogHeader>
          <DialogTitle>Versión {number} · solo lectura</DialogTitle>
          <DialogDescription>Versión publicada: no se puede editar. Los documentos ya emitidos conservan la suya.</DialogDescription>
        </DialogHeader>
        {version.isPending && (
          <div role="status" aria-label="Cargando versión">
            <Skeleton className="h-48" />
          </div>
        )}
        {version.isError && <LoadErrorState bare error={version.error} onRetry={version.refetch} resource="la versión" />}
        {content && (
          <dl className="flex max-h-[60vh] flex-col gap-3 overflow-y-auto">
            {textFieldsOf(templateKey).map((field) => (
              <div key={field.name} className="flex flex-col gap-0.5">
                <dt className="text-size-12 font-semibold text-ink-faint">{field.label}</dt>
                <dd className="text-size-14 text-ink">{content[field.name] || 'Sin completar'}</dd>
              </div>
            ))}
            <SignerLine label="Primer firmante" signer={content.firstSigner} />
            <SignerLine label="Segundo firmante" signer={content.secondSigner} />
          </dl>
        )}
      </DialogContent>
    </Dialog>
  );
}

function VersionRow({ version, current, canUseAsBase, busy, onView, onUseAsBase }) {
  return (
    <li className="flex flex-col gap-2 rounded-2xl border border-border p-3">
      <div className="flex items-start justify-between gap-2">
        <b className="text-size-14 text-ink">Versión {version.versionNumber}</b>
        <StatusPill tone={current ? 'success' : 'neutral'}>{current ? 'Vigente' : 'Anterior'}</StatusPill>
      </div>
      <span className="text-size-12-5 text-ink-faint">
        {formatPublishedAt(version.publishedAt)} · {version.publishedBy}
      </span>
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="min-h-11 md:min-h-7"
          aria-label={`Ver versión ${version.versionNumber}`}
          onClick={(event) => onView(event, version.versionNumber)}
        >
          Ver
        </Button>
        {canUseAsBase && !current && (
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="min-h-11 md:min-h-7"
            disabled={busy}
            aria-label={`Usar la versión ${version.versionNumber} como base`}
            onClick={() => onUseAsBase(version.versionNumber)}
          >
            Usar como base
          </Button>
        )}
      </div>
    </li>
  );
}

export function TemplateHistorySheet({ open, onOpenChange, templateKey, template, canUseAsBase, onUseAsBase, onCloseAutoFocus }) {
  const versions = useTemplateVersions(templateKey, open);
  const loadVersion = useLoadTemplateVersion(templateKey);
  const [viewing, setViewing] = useState(null);
  const [pendingBase, setPendingBase] = useState(null);
  const [error, setError] = useState(null);
  const viewFocus = useReturnFocus();
  const currentNumber = template.published?.versionNumber ?? null;

  async function applyBase(number) {
    setError(null);
    try {
      const version = await loadVersion.mutateAsync(number);
      onUseAsBase(formFromContent(version.content), number);
      onOpenChange(false);
    } catch (failure) {
      setError(describeError(failure).message);
    } finally {
      setPendingBase(null);
    }
  }

  function requestBase(number) {
    if (template.draft) setPendingBase(number);
    else applyBase(number);
  }

  let body;
  if (versions.isPending) {
    body = (
      <div role="status" aria-label="Cargando versiones">
        <Skeleton className="h-24" />
      </div>
    );
  } else if (versions.isError) {
    body = <LoadErrorState bare error={versions.error} onRetry={versions.refetch} resource="las versiones" />;
  } else if (versions.data.length === 0) {
    body = (
      <div className="flex flex-col gap-1">
        <b className="text-size-14 text-ink">Todavía no hay versiones</b>
        <p className="text-size-13 text-ink-soft">La primera que publiques aparece acá.</p>
      </div>
    );
  } else {
    const sorted = [...versions.data].sort((a, b) => b.versionNumber - a.versionNumber);
    body = (
      <ul className="flex flex-col gap-3">
        {sorted.map((version) => (
          <VersionRow
            key={version.versionNumber}
            version={version}
            current={version.versionNumber === currentNumber}
            canUseAsBase={canUseAsBase}
            busy={loadVersion.isPending}
            onView={(event, number) => {
              viewFocus.remember(event);
              setViewing(number);
            }}
            onUseAsBase={requestBase}
          />
        ))}
      </ul>
    );
  }

  return (
    <>
      <Sheet open={open} onOpenChange={onOpenChange}>
        <SheetContent className="w-full overflow-y-auto sm:max-w-md" onCloseAutoFocus={onCloseAutoFocus}>
          <SheetHeader>
            <SheetTitle>Historial de versiones</SheetTitle>
            <SheetDescription>Cada versión publicada queda guardada tal cual y no se puede editar.</SheetDescription>
          </SheetHeader>
          <div className="flex flex-col gap-3 px-4 pb-4">
            {error && (
              <p role="alert" className="text-size-13 font-medium text-danger">
                {error}
              </p>
            )}
            {body}
          </div>
        </SheetContent>
      </Sheet>
      <VersionDialog templateKey={templateKey} number={viewing} onClose={() => setViewing(null)} onCloseAutoFocus={viewFocus.onCloseAutoFocus} />
      <ConfirmActionDialog
        open={pendingBase != null}
        onOpenChange={(next) => !next && setPendingBase(null)}
        title="Reemplazar el borrador"
        description={`Ya hay un borrador guardado. Si seguís, el contenido de la versión ${pendingBase} lo reemplaza cuando lo guardes.`}
        confirmLabel="Reemplazar borrador"
        confirmVariant="primary"
        pending={loadVersion.isPending}
        onConfirm={() => applyBase(pendingBase)}
      />
    </>
  );
}
