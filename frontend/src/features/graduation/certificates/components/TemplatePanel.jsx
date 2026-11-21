import { useEffect, useState } from 'react';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Notice } from '@/components/Notice';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { Button } from '@/components/ui/button';
import { describeError } from '@/api/describeError';
import { TemplatePreviewDialog } from '@/features/graduation/certificates/components/TemplatePreviewDialog';
import { TemplateSystemData } from '@/features/graduation/certificates/components/TemplateSystemData';
import { TemplateVersions } from '@/features/graduation/certificates/components/TemplateVersions';
import { TemplateLogoField } from '@/features/graduation/certificates/components/TemplateLogoField';
import { TemplateSignatories } from '@/features/graduation/certificates/components/TemplateSignatories';
import { TemplateTextFields } from '@/features/graduation/certificates/components/TemplateTextFields';
import {
  useCertificateTemplate,
  useDiscardTemplateDraft,
  useGraduationSignatories,
  usePreviewTemplate,
  usePublishTemplate,
  useSaveTemplateDraft,
} from '@/features/graduation/certificates/hooks/useCertificateTemplate';
import {
  baselineOf,
  canPublish,
  contentFromForm,
  isSameForm,
  previewSourceOf,
  statusOf,
  validateForm,
} from '@/features/graduation/certificates/lib/certificateTemplate';

function PanelSkeleton() {
  return (
    <div className="flex flex-col gap-4" aria-label="Cargando">
      <Skeleton className="h-14" />
      <Skeleton className="h-56" />
      <Skeleton className="h-40" />
    </div>
  );
}

export function TemplatePanel({ kind, documentName, readOnly, edit, onEdit, onSettled }) {
  const template = useCertificateTemplate(kind);
  const signatories = useGraduationSignatories();
  const save = useSaveTemplateDraft(kind);
  const discard = useDiscardTemplateDraft(kind);
  const publish = usePublishTemplate(kind);
  const previewTemplate = usePreviewTemplate(kind);
  const [preview, setPreview] = useState(null);
  const [confirmPublish, setConfirmPublish] = useState(false);
  const previewUrl = preview?.url;

  useEffect(() => () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
  }, [previewUrl]);
  const [actionError, setActionError] = useState(null);
  const [savedNotice, setSavedNotice] = useState(false);
  const [confirmDiscard, setConfirmDiscard] = useState(false);

  if (template.isPending) return <PanelSkeleton />;
  if (template.isError) {
    return <LoadErrorState error={template.error} onRetry={template.refetch} resource="la plantilla" />;
  }

  const data = template.data;
  const baseline = baselineOf(data);
  const form = edit ?? baseline;
  const dirty = !isSameForm(form, baseline);
  const errors = validateForm(form);
  const hasErrors = Object.keys(errors).length > 0;
  const status = statusOf({ template: data, dirty });
  const neverPublished = !data.published && !data.draft;

  function change(next) {
    setSavedNotice(false);
    setActionError(null);
    onEdit(isSameForm(next, baseline) ? null : next);
  }

  async function saveDraft() {
    setActionError(null);
    try {
      await save.mutateAsync(contentFromForm(form));
      onSettled();
      setSavedNotice(true);
    } catch (error) {
      setActionError(describeError(error).message);
    }
  }

  async function discardDraft() {
    setActionError(null);
    try {
      await discard.mutateAsync();
      onSettled();
      setSavedNotice(false);
    } catch (error) {
      setActionError(describeError(error).message);
    } finally {
      setConfirmDiscard(false);
    }
  }

  const previewSource = previewSourceOf({ isDirector: !readOnly, template: data });
  const publishable = canPublish({ template: data, dirty, form, signatories: signatories.data });
  const nextVersion = data.draft?.versionNumber;

  async function publishDraft() {
    setActionError(null);
    try {
      await publish.mutateAsync();
      onSettled();
      setSavedNotice(false);
    } catch (error) {
      setActionError(describeError(error).message);
    } finally {
      setConfirmPublish(false);
    }
  }

  async function openPreview() {
    setActionError(null);
    try {
      const { blob } = await previewTemplate.mutateAsync(previewSource);
      setPreview({ url: URL.createObjectURL(blob), source: previewSource });
    } catch (error) {
      setActionError(describeError(error).message);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {readOnly && (
        <Notice tone="neutral" title="Solo lectura.">
          Esta plantilla la edita el Director. Podés ver cómo está configurada, pero no modificarla.
        </Notice>
      )}

      <GlassCard className="flex flex-wrap items-center justify-between gap-3 p-4">
        <div className="flex flex-wrap items-center gap-3">
          <StatusPill tone={status.tone}>{status.label}</StatusPill>
          <span className="text-size-13 text-ink-faint" aria-live="polite">
            {dirty
              ? 'Guardá el borrador para no perder lo que editaste.'
              : savedNotice
                ? 'Borrador guardado.'
                : 'Las emisiones nuevas usan la versión publicada.'}
          </span>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant="outline"
            disabled={previewSource === null || (!readOnly && dirty)}
            loading={previewTemplate.isPending}
            onClick={openPreview}
          >
            Vista previa
          </Button>
          {!readOnly && (
            <>
            <Button
              type="button"
              variant="outline"
              disabled={!data.draft || dirty}
              onClick={() => setConfirmDiscard(true)}
            >
              Descartar borrador
            </Button>
            <Button type="button" variant="primary" disabled={!dirty || hasErrors} loading={save.isPending} onClick={saveDraft}>
              Guardar borrador
            </Button>
            <Button type="button" variant="primary" disabled={!publishable} onClick={() => setConfirmPublish(true)}>
              Publicar
            </Button>
            </>
          )}
        </div>
      </GlassCard>

      {actionError && (
        <p role="alert" className="text-size-14 font-medium text-danger">
          {actionError}
        </p>
      )}

      {!readOnly && data.draft && !dirty && !publishable && (
        <p className="text-size-13 text-ink-soft">Elegí los dos firmantes para poder publicar.</p>
      )}

      {neverPublished && !dirty && (
        <GlassCard className="p-4">
          <EmptyState
            icon="📄"
            title="Todavía no hay plantilla"
            description={
              readOnly
                ? 'El Director todavía no configuró este certificado.'
                : `Completá los textos, el logo y los firmantes de «${documentName}» y guardá el borrador.`
            }
          />
        </GlassCard>
      )}

      <GlassCard className="flex flex-col gap-4 p-4 md:p-5">
        <h2 className="font-heading text-size-15 font-semibold text-ink">Instituto y textos</h2>
        <TemplateLogoField
          kind={kind}
          logoImageId={form.logoImageId}
          readOnly={readOnly}
          onChange={(logoImageId) => change({ ...form, logoImageId })}
        />
        <TemplateTextFields form={form} readOnly={readOnly} onChange={change} />
      </GlassCard>

      <GlassCard className="flex flex-col gap-3 p-4 md:p-5">
        <h2 className="font-heading text-size-15 font-semibold text-ink">Firmantes</h2>
        <p className="text-size-13 text-ink-soft">Nombre y cargo salen de la lista de firmantes con permiso para certificados de egreso.</p>
        <TemplateSignatories form={form} readOnly={readOnly} signatories={signatories} errors={errors} onChange={change} />
      </GlassCard>

      <TemplateSystemData />
      <TemplateVersions kind={kind} />

      <TemplatePreviewDialog preview={preview} onClose={() => setPreview(null)} />
      <ConfirmActionDialog
        open={confirmPublish}
        onOpenChange={setConfirmPublish}
        title={`Publicar la versión ${nextVersion}`}
        description={`Las emisiones nuevas de «${documentName}» van a usar esta versión. Los certificados ya emitidos conservan la suya y no cambian. La versión publicada no se puede editar.`}
        confirmLabel={`Publicar v${nextVersion}`}
        confirmVariant="primary"
        pending={publish.isPending}
        onConfirm={publishDraft}
      />
      <ConfirmActionDialog
        open={confirmDiscard}
        onOpenChange={setConfirmDiscard}
        title="Descartar el borrador"
        description="El borrador se elimina y la plantilla vuelve a la última versión publicada. Perdés lo que editaste desde entonces."
        confirmLabel="Descartar borrador"
        pending={discard.isPending}
        onConfirm={discardDraft}
      />
    </div>
  );
}
