import { useState } from 'react';
import { History, Eye, Lock } from 'lucide-react';
import { describeError } from '@/api/describeError';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Notice } from '@/components/Notice';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { useReturnFocus } from '@/features/graduation/certificates/hooks/useReturnFocus';
import { TemplateEditActions, TemplateStatusHint } from '@/features/graduation/certificates/components/TemplateActionsBar';
import { TemplateHistorySheet } from '@/features/graduation/certificates/components/TemplateHistorySheet';
import { TemplateLogoField } from '@/features/graduation/certificates/components/TemplateLogoField';
import { TemplatePreviewDialog, TemplatePreviewPanel } from '@/features/graduation/certificates/components/TemplatePreview';
import { TemplateSignatories } from '@/features/graduation/certificates/components/TemplateSignatories';
import { TemplateTextFields } from '@/features/graduation/certificates/components/TemplateTextFields';
import {
  useCertificateTemplate,
  useDiscardTemplateDraft,
  usePublishTemplate,
  useSaveTemplateDraft,
  useTemplateSignatories,
} from '@/features/graduation/certificates/hooks/useCertificateTemplate';
import {
  baselineOf,
  canPublish,
  contentFromForm,
  isSameForm,
  publishBlockerOf,
  statusOf,
  tabOf,
  validateForm,
} from '@/features/graduation/certificates/lib/certificateTemplate';

const WIDE_QUERY = '(min-width: 1280px)';

function PanelSkeleton() {
  return (
    <div className="flex flex-col gap-4" role="status" aria-label="Cargando">
      <Skeleton className="h-14" />
      <Skeleton className="h-56" />
      <Skeleton className="h-40" />
    </div>
  );
}

function PanelLoadError({ error, onRetry }) {
  if (error?.status === 422) {
    return (
      <GlassCard className="flex flex-1 flex-col p-12" role="alert">
        <EmptyState
          icon="🚧"
          title="Esta plantilla todavía no está disponible"
          description={describeError(error).message}
          action={<Button onClick={onRetry}>Reintentar</Button>}
        />
      </GlassCard>
    );
  }
  return <LoadErrorState error={error} onRetry={onRetry} resource="la plantilla" />;
}

function defaultHintOf({ template }) {
  if (template.draft) return 'Todavía no se usa: falta publicar.';
  if (template.published) return 'Las emisiones nuevas usan la versión publicada.';
  return 'Completá los datos y guardá el borrador para empezar.';
}

export function TemplatePanel({ templateKey, readOnly, edit, onEdit, onSettled }) {
  const tab = tabOf(templateKey);
  const template = useCertificateTemplate(templateKey);
  const signatories = useTemplateSignatories(templateKey);
  const save = useSaveTemplateDraft(templateKey);
  const discard = useDiscardTemplateDraft(templateKey);
  const publish = usePublishTemplate(templateKey);
  const wide = useMediaQuery(WIDE_QUERY);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [confirmPublish, setConfirmPublish] = useState(false);
  const [confirmDiscard, setConfirmDiscard] = useState(false);
  const [actionError, setActionError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [basedOnVersionNumber, setBasedOnVersionNumber] = useState(null);
  const historyFocus = useReturnFocus();
  const previewFocus = useReturnFocus();
  const publishFocus = useReturnFocus();
  const discardFocus = useReturnFocus();

  if (template.isPending) return <PanelSkeleton />;
  if (template.isError) return <PanelLoadError error={template.error} onRetry={template.refetch} />;

  const data = template.data;
  const baseline = baselineOf(data);
  const form = edit ?? baseline;
  const dirty = !isSameForm(form, baseline);
  const errors = validateForm(form, templateKey, data.allowedMarkers);
  const hasErrors = Object.keys(errors).length > 0;
  const status = statusOf({ template: data, dirty });
  const neverConfigured = !data.published && !data.draft;
  const showRequired = dirty || !neverConfigured;
  const blocker = publishBlockerOf({ template: data, dirty, form, signatories: signatories.data, templateKey, allowedMarkers: data.allowedMarkers });
  const publishable = canPublish({ template: data, dirty, form, signatories: signatories.data, templateKey, allowedMarkers: data.allowedMarkers });
  const hint = notice ?? blocker ?? defaultHintOf({ template: data });
  const nextVersion = data.draft?.versionNumber;

  function change(next) {
    setNotice(null);
    setActionError(null);
    onEdit(isSameForm(next, baseline) ? null : next);
  }

  async function saveDraft() {
    setActionError(null);
    try {
      await save.mutateAsync({ content: contentFromForm(form), basedOnVersionNumber });
      setBasedOnVersionNumber(null);
      onSettled();
      setNotice('Borrador guardado.');
    } catch (error) {
      setActionError(describeError(error).message);
    }
  }

  async function discardDraft() {
    setActionError(null);
    try {
      await discard.mutateAsync();
      setBasedOnVersionNumber(null);
      onSettled();
      setNotice('Borrador descartado.');
    } catch (error) {
      setActionError(describeError(error).message);
    } finally {
      setConfirmDiscard(false);
    }
  }

  async function publishDraft() {
    setActionError(null);
    try {
      await publish.mutateAsync();
      onSettled();
      setNotice(`Versión ${nextVersion} publicada.`);
    } catch (error) {
      setActionError(describeError(error).message);
    } finally {
      setConfirmPublish(false);
    }
  }

  function useVersionAsBase(baseForm, number) {
    setActionError(null);
    onEdit(isSameForm(baseForm, baseline) ? null : baseForm);
    setBasedOnVersionNumber(number);
    setNotice(`Cargamos la v${number} como borrador. Guardalo para conservarlo.`);
  }

  const previewProps = { templateKey, template: data, canSeeDraft: !readOnly, dirty };

  return (
    <div className="flex flex-col gap-4 xl:grid xl:grid-cols-[minmax(0,1fr)_400px] xl:items-start">
      <div className="flex min-w-0 flex-col gap-4">
        {readOnly && (
          <Notice tone="neutral" title="Solo lectura.">
            <Lock aria-hidden="true" className="mr-1 inline size-3.5 align-text-bottom" />
            Esta plantilla la edita el Director. Podés ver cómo está configurada, pero no modificarla.
          </Notice>
        )}

        <div className="flex flex-wrap items-center justify-end gap-2">
          <Button type="button" variant="outline" className="min-h-11 md:min-h-8" onClick={(event) => {
              historyFocus.remember(event);
              setHistoryOpen(true);
            }}>
            <History aria-hidden="true" className="size-4" />
            Historial
          </Button>
          {!wide && (
            <Button type="button" variant="outline" className="min-h-11 md:min-h-8" onClick={(event) => {
                previewFocus.remember(event);
                setPreviewOpen(true);
              }}>
              <Eye aria-hidden="true" className="size-4" />
              Vista previa
            </Button>
          )}
        </div>

        {readOnly && <TemplateStatusHint readOnly status={status} hint={hint} />}

        {actionError && (
          <p role="alert" className="text-size-14 font-medium text-danger">
            {actionError}
          </p>
        )}

        {neverConfigured && !dirty && (
          <GlassCard className="p-4">
            <EmptyState
              icon="📄"
              title="Todavía no hay plantilla"
              description={
                readOnly
                  ? 'El Director todavía no configuró este documento.'
                  : `Completá textos, logo y firmantes de «${tab.documentName}», guardá el borrador y publicá.`
              }
            />
          </GlassCard>
        )}

        <GlassCard className="flex flex-col gap-4 p-4 md:p-5">
          <h2 className="font-heading text-size-15 font-semibold text-ink">Instituto y textos</h2>
          <TemplateLogoField
            templateKey={templateKey}
            logoImageId={form.logoImageId}
            readOnly={readOnly}
            onChange={(logoImageId) => change({ ...form, logoImageId })}
          />
          <TemplateTextFields templateKey={templateKey} allowedMarkers={data.allowedMarkers} form={form} readOnly={readOnly} showRequired={showRequired} onChange={change} />
        </GlassCard>

        <GlassCard className="flex flex-col gap-3 p-4 md:p-5">
          <h2 className="font-heading text-size-15 font-semibold text-ink">Firmantes</h2>
          <TemplateSignatories
            documentName={tab.documentName}
            form={form}
            readOnly={readOnly}
            signatories={signatories}
            errors={errors}
            showRequired={showRequired}
            onChange={change}
          />
        </GlassCard>

        {!readOnly && (
          <TemplateStatusHint
            status={status}
            hint={hint}
            actions={
              <TemplateEditActions
                canDiscard={Boolean(data.draft) && !dirty}
                canSave={dirty && !hasErrors}
                canPublish={publishable}
                saving={save.isPending}
                onDiscard={(event) => {
                  discardFocus.remember(event);
                  setConfirmDiscard(true);
                }}
                onSave={saveDraft}
                onPublish={(event) => {
                  publishFocus.remember(event);
                  setConfirmPublish(true);
                }}
              />
            }
          />
        )}
      </div>

      {wide && (
        <aside aria-label="Vista previa">
          <TemplatePreviewPanel {...previewProps} />
        </aside>
      )}

      {!wide && <TemplatePreviewDialog open={previewOpen} onOpenChange={setPreviewOpen} onCloseAutoFocus={previewFocus.onCloseAutoFocus} {...previewProps} />}
      <TemplateHistorySheet
        open={historyOpen}
        onOpenChange={setHistoryOpen}
        templateKey={templateKey}
        template={data}
        canUseAsBase={!readOnly}
        onUseAsBase={useVersionAsBase}
        onCloseAutoFocus={historyFocus.onCloseAutoFocus}
      />
      <ConfirmActionDialog
        open={confirmPublish}
        onOpenChange={setConfirmPublish}
        title={`Publicar la versión ${nextVersion}`}
        description={`Las emisiones nuevas de «${tab.documentName}» van a usar esta versión. Los documentos ya emitidos conservan la suya y no cambian: cada versión publicada queda guardada tal cual y no se puede editar. Para corregir algo después, se publica una versión nueva.`}
        confirmLabel={`Publicar v${nextVersion}`}
        confirmVariant="primary"
        pending={publish.isPending}
        onConfirm={publishDraft}
        onCloseAutoFocus={publishFocus.onCloseAutoFocus}
      />
      <ConfirmActionDialog
        open={confirmDiscard}
        onOpenChange={setConfirmDiscard}
        title="Descartar el borrador"
        description="Se elimina y la plantilla vuelve a la última versión publicada. Perdés lo que editaste desde entonces."
        confirmLabel="Descartar borrador"
        pending={discard.isPending}
        onConfirm={discardDraft}
        onCloseAutoFocus={discardFocus.onCloseAutoFocus}
      />
    </div>
  );
}
