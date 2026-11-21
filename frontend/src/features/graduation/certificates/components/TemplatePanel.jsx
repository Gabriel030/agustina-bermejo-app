import { useState } from 'react';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Notice } from '@/components/Notice';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { Button } from '@/components/ui/button';
import { describeError } from '@/api/describeError';
import { TemplateLogoField } from '@/features/graduation/certificates/components/TemplateLogoField';
import { TemplateSignatories } from '@/features/graduation/certificates/components/TemplateSignatories';
import { TemplateTextFields } from '@/features/graduation/certificates/components/TemplateTextFields';
import {
  useCertificateTemplate,
  useDiscardTemplateDraft,
  useGraduationSignatories,
  useSaveTemplateDraft,
} from '@/features/graduation/certificates/hooks/useCertificateTemplate';
import {
  baselineOf,
  contentFromForm,
  isSameForm,
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
        {!readOnly && (
          <div className="flex flex-wrap gap-2">
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
          </div>
        )}
      </GlassCard>

      {actionError && (
        <p role="alert" className="text-size-14 font-medium text-danger">
          {actionError}
        </p>
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
