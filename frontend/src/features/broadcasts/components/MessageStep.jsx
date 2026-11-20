import { useState } from 'react';
import { toast } from 'sonner';
import { GlassCard } from '@/components/GlassCard';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { NativeSelect } from '@/components/ui/native-select';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { useMessageTemplates, useRenderPreview, useSaveMessageTemplate } from '../hooks/useBroadcasts';
import { TEMPLATE_VARIABLES, validateMessage } from '../lib/broadcastWizard';

export function MessageStep({ state, onChange, preview, onExcludeMissing }) {
  const { data: templates = [] } = useMessageTemplates(state.channel);
  const saveTemplate = useSaveMessageTemplate();
  const [personId, setPersonId] = useState(null);
  const [confirmOverwrite, setConfirmOverwrite] = useState(false);
  const errors = validateMessage(state);
  const people = (preview?.sample ?? []).filter((row) => !row.exclusionReason);
  const selectedPerson = personId ?? people[0]?.personId ?? null;
  const rendered = useRenderPreview({ channel: state.channel, subject: state.subject, bodyText: state.bodyText, personId: selectedPerson });
  const missing = preview?.excludedByReason?.MISSING_TEMPLATE_VARIABLE ?? 0;
  const selectedTemplate = templates.find((template) => template.id === state.templateId);
  const templateDirty =
    selectedTemplate && (selectedTemplate.subject !== state.subject || selectedTemplate.bodyText !== state.bodyText);

  function pickTemplate(id) {
    const template = templates.find((item) => item.id === Number(id));
    onChange(template ? { templateId: template.id, subject: template.subject, bodyText: template.bodyText } : { templateId: null });
  }

  async function persist(id) {
    try {
      await saveTemplate.mutateAsync({
        id,
        name: id ? selectedTemplate.name : state.name || 'Plantilla nueva',
        category: 'INSTITUTIONAL',
        channel: state.channel,
        subject: state.subject,
        bodyText: state.bodyText,
      });
      toast.success(id ? 'Actualizamos la plantilla.' : 'Guardamos una plantilla nueva.');
      setConfirmOverwrite(false);
    } catch (error) {
      toast.error(error.message ?? 'No pudimos guardar la plantilla.');
    }
  }

  return (
    <div className="grid gap-4 md:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
      <GlassCard className="flex flex-col gap-4 p-5">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="template-picker" className="text-size-13 font-semibold text-ink">
            Plantilla
          </label>
          <NativeSelect id="template-picker" value={state.templateId ?? ''} onChange={(event) => pickTemplate(event.target.value)}>
            <option value="">En blanco</option>
            {templates.map((template) => (
              <option key={template.id} value={template.id}>
                {template.name}
              </option>
            ))}
          </NativeSelect>
        </div>
        {state.channel === 'EMAIL' && (
          <div className="flex flex-col gap-1.5">
            <label htmlFor="broadcast-subject" className="text-size-13 font-semibold text-ink">
              Asunto
            </label>
            <Input id="broadcast-subject" value={state.subject} onChange={(event) => onChange({ subject: event.target.value })} />
            {errors.subject && <p className="text-size-12 text-danger">{errors.subject}</p>}
          </div>
        )}
        <div className="flex flex-col gap-1.5">
          <label htmlFor="broadcast-body" className="text-size-13 font-semibold text-ink">
            Mensaje
          </label>
          <Textarea id="broadcast-body" rows={8} value={state.bodyText} onChange={(event) => onChange({ bodyText: event.target.value })} />
          {errors.bodyText && state.bodyText !== '' && <p className="text-size-12 text-danger">{errors.bodyText}</p>}
          <div className="flex flex-wrap gap-1.5" aria-label="Variables">
            {TEMPLATE_VARIABLES.map((name) => (
              <Button key={name} type="button" size="sm" variant="outline" onClick={() => onChange({ bodyText: `${state.bodyText}{{${name}}}` })}>
                {`{{${name}}}`}
              </Button>
            ))}
          </div>
        </div>
        {missing > 0 && (
          <div role="status" className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-warning-bg px-3 py-2 text-size-13 text-ink">
            <span>
              {missing === 1 ? 'A 1 destinatario le falta un dato del mensaje.' : `A ${missing} destinatarios les falta un dato del mensaje.`}
            </span>
            {state.excludeMissingVariables ? (
              <span className="font-semibold">Excluidos del envío</span>
            ) : (
              <Button type="button" size="sm" variant="outline" onClick={onExcludeMissing}>
                {`Excluir a esos ${missing}`}
              </Button>
            )}
          </div>
        )}
        {templateDirty && (
          <div className="flex flex-wrap gap-2">
            <Button type="button" size="sm" variant="gradient" loading={saveTemplate.isPending} onClick={() => persist(undefined)}>
              Guardar como nueva
            </Button>
            <Button type="button" size="sm" variant="outline" onClick={() => setConfirmOverwrite(true)}>
              Sobrescribir plantilla
            </Button>
          </div>
        )}
      </GlassCard>
      <GlassCard className="flex flex-col gap-3 p-5">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="preview-person" className="text-size-13 font-semibold text-ink">
            Vista previa para
          </label>
          <NativeSelect id="preview-person" value={selectedPerson ?? ''} onChange={(event) => setPersonId(Number(event.target.value))}>
            {people.map((row) => (
              <option key={row.personId} value={row.personId}>
                {row.fullName}
              </option>
            ))}
          </NativeSelect>
        </div>
        {rendered.isError && (
          <p role="alert" className="text-size-13 text-danger">
            {rendered.error.message}
          </p>
        )}
        {rendered.data && (
          <div className="flex flex-col gap-2 rounded-xl bg-neutral-bg/60 p-3 text-size-13 text-ink">
            {state.channel === 'EMAIL' && <p className="font-semibold">{rendered.data.subject}</p>}
            <p className="whitespace-pre-wrap">{rendered.data.body}</p>
          </div>
        )}
      </GlassCard>
      <Dialog open={confirmOverwrite} onOpenChange={setConfirmOverwrite}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Sobrescribir plantilla</DialogTitle>
            <DialogDescription>Los envíos futuros que usen esta plantilla van a tomar el texto nuevo.</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" autoFocus onClick={() => setConfirmOverwrite(false)}>
              Cancelar
            </Button>
            <Button variant="destructive" onClick={() => persist(state.templateId)}>
              Sobrescribir
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
