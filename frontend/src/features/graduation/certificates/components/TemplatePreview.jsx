import { useState } from 'react';
import { describeError } from '@/api/describeError';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { useTemplatePreview } from '@/features/graduation/certificates/hooks/useCertificateTemplate';
import { previewSourceOf, previewStampOf } from '@/features/graduation/certificates/lib/certificateTemplate';
import { useBlobUrl } from '@/features/graduation/signatories/hooks/useBlobUrl';

function PreviewFrame({ templateKey, source, stamp }) {
  const preview = useTemplatePreview(templateKey, source, stamp);
  const url = useBlobUrl(preview.data ?? null);
  if (source === null) {
    return <EmptyState icon="📄" title="No hay versión publicada para previsualizar." className="py-6" />;
  }
  if (preview.isError) {
    return (
      <div role="alert" className="flex flex-col items-start gap-2">
        <p className="text-size-13 font-medium text-danger">{describeError(preview.error).message}</p>
        <Button type="button" variant="outline" size="sm" className="min-h-11 md:min-h-7" onClick={() => preview.refetch()}>
          Reintentar
        </Button>
      </div>
    );
  }
  if (!url) {
    return (
      <div role="status" aria-label="Cargando vista previa">
        <Skeleton className="aspect-[1/1.3] w-full" />
      </div>
    );
  }
  return <iframe src={url} title="Vista previa del certificado" className="aspect-[1/1.3] w-full rounded-md border border-border" />;
}

export function TemplatePreviewBody({ templateKey, template, canSeeDraft, dirty }) {
  const [showDraft, setShowDraft] = useState(false);
  const source = previewSourceOf({ showDraft, canSeeDraft, template });
  const stamp = previewStampOf({ source, template });
  const hasDraft = canSeeDraft && Boolean(template.draft);
  const switchLocked = !hasDraft || !template.published;

  return (
    <div className="flex flex-col gap-3">
      {canSeeDraft && (
        <div className="flex min-h-11 items-center gap-2">
          <Switch
            id={`preview-draft-${templateKey}`}
            checked={source === 'DRAFT'}
            disabled={switchLocked}
            onCheckedChange={setShowDraft}
          />
          <Label htmlFor={`preview-draft-${templateKey}`}>Ver borrador</Label>
        </div>
      )}
      <PreviewFrame key={`${source}-${stamp}`} templateKey={templateKey} source={source} stamp={stamp} />
      <span className="text-size-12 text-ink-faint" aria-live="polite">
        {dirty
          ? 'La vista previa muestra lo guardado. Guardá el borrador para ver tus cambios.'
          : 'Con datos de ejemplo y marca de agua. No se emite ni queda registrada.'}
      </span>
    </div>
  );
}

export function TemplatePreviewPanel(props) {
  return (
    <GlassCard className="flex flex-col gap-3 p-4 xl:sticky xl:top-4">
      <h2 className="font-heading text-size-15 font-semibold text-ink">Vista previa del PDF</h2>
      <TemplatePreviewBody {...props} />
    </GlassCard>
  );
}

export function TemplatePreviewDialog({ open, onOpenChange, onCloseAutoFocus, ...props }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl" onCloseAutoFocus={onCloseAutoFocus}>
        <DialogHeader>
          <DialogTitle>Vista previa del PDF</DialogTitle>
          <DialogDescription>Así se ve el documento con datos de ejemplo.</DialogDescription>
        </DialogHeader>
        <div className="max-h-[70vh] overflow-y-auto">
          <TemplatePreviewBody {...props} />
        </div>
      </DialogContent>
    </Dialog>
  );
}
