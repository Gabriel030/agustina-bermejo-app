import { useRef, useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { ChoiceChip } from '@/components/ChoiceChip';
import { Notice } from '@/components/Notice';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { describeError } from '@/api/describeError';
import { SignatureField } from '@/features/graduation/signatories/components/SignatureField';
import {
  useCreateSignatory,
  useDeleteSignature,
  useSetSignatoryActive,
  useSignatorySignature,
  useUpdateSignatory,
  useUploadSignature,
} from '@/features/graduation/signatories/hooks/useCertificateSignatories';
import {
  DOCUMENT_TYPE_OPTIONS,
  NAME_MAX_LENGTH,
  OTHER_ROLE,
  ROLE_MAX_LENGTH,
  ROLE_SUGGESTIONS,
  deactivationDescriptionOf,
  draftOf,
  emptyDraft,
  isDraftChanged,
  isDraftComplete,
  lostTemplateUsages,
  saveBodyOf,
  templateUsageLabelOf,
  toggleDocumentType,
} from '@/features/graduation/signatories/lib/certificateSignatories';

function reportFailure(error) {
  toast.error(describeError(error).message);
}

function UsageList({ usages }) {
  if (usages.length === 0) return <span className="text-size-13 text-ink">Ninguna plantilla por ahora.</span>;
  return (
    <ul className="flex list-disc flex-col gap-1 pl-5 text-size-13 text-ink">
      {usages.map((usage) => (
        <li key={`${usage.documentType}-${usage.versionNumber}`}>{templateUsageLabelOf(usage)}</li>
      ))}
    </ul>
  );
}

export function SignatoryForm({ signatory, canEdit, onClose }) {
  const isNew = signatory == null;
  const [draft, setDraft] = useState(() => (isNew ? emptyDraft() : draftOf(signatory)));
  const [stagedFile, setStagedFile] = useState(null);
  const [confirmingDeactivation, setConfirmingDeactivation] = useState(false);
  const roleInputRef = useRef(null);
  const fieldId = `signatory-${signatory?.id ?? 'new'}`;

  const signature = useSignatorySignature(signatory ?? { id: 0, hasSignature: false }, !isNew);
  const create = useCreateSignatory();
  const update = useUpdateSignatory();
  const setActive = useSetSignatoryActive();
  const upload = useUploadSignature();
  const removeSignature = useDeleteSignature();

  const editable = canEdit;
  const saving = create.isPending || update.isPending;
  const changed = isNew ? isDraftComplete(draft) : isDraftChanged(draft, signatory) && isDraftComplete(draft);
  const lost = isNew ? [] : lostTemplateUsages(signatory, draft);
  const otherSelected = draft.role.trim() !== '' && !ROLE_SUGGESTIONS.slice(0, -1).includes(draft.role);

  function patch(change) {
    setDraft((current) => ({ ...current, ...change }));
  }

  function pickRole(suggestion) {
    if (suggestion === OTHER_ROLE) {
      patch({ role: '' });
      roleInputRef.current?.focus();
      return;
    }
    patch({ role: suggestion });
  }

  async function save() {
    try {
      if (isNew) {
        const created = await create.mutateAsync(saveBodyOf(draft));
        if (stagedFile) {
          try {
            await upload.mutateAsync({ id: created.id, file: stagedFile });
          } catch (error) {
            reportFailure(error);
          }
        }
        toast.success('Firmante creado.');
      } else {
        await update.mutateAsync({ id: signatory.id, body: saveBodyOf(draft) });
        toast.success('Cambios guardados.');
      }
      onClose();
    } catch (error) {
      reportFailure(error);
    }
  }

  async function replaceSignature(file) {
    if (isNew) {
      setStagedFile(file);
      return;
    }
    try {
      await upload.mutateAsync({ id: signatory.id, file });
      toast.success('Firma actualizada.');
    } catch (error) {
      reportFailure(error);
    }
  }

  async function dropSignature() {
    if (isNew) {
      setStagedFile(null);
      return;
    }
    try {
      await removeSignature.mutateAsync(signatory.id);
      toast.success('Firma quitada.');
    } catch (error) {
      reportFailure(error);
    }
  }

  async function changeActivity(active) {
    try {
      await setActive.mutateAsync({ id: signatory.id, active });
      toast.success(active ? 'Firmante activado.' : 'Firmante desactivado.');
      setConfirmingDeactivation(false);
    } catch (error) {
      setConfirmingDeactivation(false);
      reportFailure(error);
    }
  }

  const signatureBlob = isNew ? stagedFile : (signatory.hasSignature ? signature.data ?? null : null);

  return (
    <div className="flex flex-col gap-5 px-1 pb-2 pt-3">
      <div className="grid gap-4 md:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor={`${fieldId}-name`} className="text-size-13 font-semibold text-ink-soft">
            Nombre y apellido
          </Label>
          <Input
            id={`${fieldId}-name`}
            value={draft.name}
            maxLength={NAME_MAX_LENGTH}
            readOnly={!editable}
            className={editable ? undefined : 'cursor-default'}
            placeholder="Ej.: Dra. Lucía Fernández"
            onChange={(event) => patch({ name: event.target.value })}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor={`${fieldId}-role`} className="text-size-13 font-semibold text-ink-soft">
            Rol o cargo
          </Label>
          <Input
            id={`${fieldId}-role`}
            ref={roleInputRef}
            value={draft.role}
            maxLength={ROLE_MAX_LENGTH}
            readOnly={!editable}
            className={editable ? undefined : 'cursor-default'}
            placeholder="Ej.: Secretaria Legal"
            onChange={(event) => patch({ role: event.target.value })}
          />
          {editable && (
            <div role="group" aria-label="Sugerencias de rol" className="flex flex-wrap gap-2">
              {ROLE_SUGGESTIONS.map((suggestion) => (
                <ChoiceChip
                  key={suggestion}
                  size="xs"
                  selected={suggestion === OTHER_ROLE ? otherSelected : draft.role === suggestion}
                  onClick={() => pickRole(suggestion)}
                >
                  {suggestion}
                </ChoiceChip>
              ))}
            </div>
          )}
        </div>
      </div>

      <SignatureField
        blob={signatureBlob}
        loading={!isNew && signatory.hasSignature && signature.isPending}
        editable={editable}
        pending={upload.isPending || removeSignature.isPending}
        signatoryName={isNew ? null : signatory.name}
        onSelect={replaceSignature}
        onRemove={dropSignature}
      />

      <fieldset className="flex flex-col gap-1">
        <legend className="mb-1 text-size-13 font-semibold text-ink-soft">Permisos por tipo de documento</legend>
        {DOCUMENT_TYPE_OPTIONS.map((option) => (
          <Label key={option.value} className="min-h-9 cursor-pointer gap-3 text-size-14 font-normal text-ink">
            <Checkbox
              checked={draft.allowedDocumentTypes.includes(option.value)}
              disabled={!editable}
              onCheckedChange={(checked) =>
                patch({ allowedDocumentTypes: toggleDocumentType(draft.allowedDocumentTypes, option.value, checked === true) })
              }
            />
            {option.label}
          </Label>
        ))}
        {lost.length > 0 && (
          <Notice tone="warning" className="mt-2">
            Está elegido en {lost.map((usage) => usage.templateName).join(', ')}. Si le quitás el permiso, esas plantillas quedan sin este firmante hasta que elijas otro.
          </Notice>
        )}
      </fieldset>

      {!isNew && (
        <div className="flex flex-col gap-1.5">
          <span className="text-size-13 font-semibold text-ink-soft">Se usa en</span>
          <UsageList usages={signatory.usedInTemplates} />
        </div>
      )}

      {!isNew && (
        <Notice>Cambiar el nombre, el cargo o la firma no altera los documentos ya emitidos.</Notice>
      )}

      {editable && (
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4">
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="gradient" loading={saving} disabled={!changed} onClick={save}>
              {isNew ? 'Crear firmante' : 'Guardar cambios'}
            </Button>
            <Button type="button" variant="outline" disabled={saving} onClick={onClose}>
              Cancelar
            </Button>
          </div>
          {!isNew &&
            (signatory.active ? (
              <Button type="button" variant="ghost-destructive" onClick={() => setConfirmingDeactivation(true)}>
                Desactivar
              </Button>
            ) : (
              <Button type="button" variant="outline" loading={setActive.isPending} onClick={() => changeActivity(true)}>
                Activar
              </Button>
            ))}
        </div>
      )}

      {!isNew && (
        <ConfirmActionDialog
          open={confirmingDeactivation}
          onOpenChange={setConfirmingDeactivation}
          title={`Desactivar a ${signatory.name}`}
          description={deactivationDescriptionOf(signatory)}
          confirmLabel="Desactivar firmante"
          pending={setActive.isPending}
          onConfirm={() => changeActivity(false)}
        >
          {signatory.usedInTemplates.length > 0 && <UsageList usages={signatory.usedInTemplates} />}
        </ConfirmActionDialog>
      )}
    </div>
  );
}
