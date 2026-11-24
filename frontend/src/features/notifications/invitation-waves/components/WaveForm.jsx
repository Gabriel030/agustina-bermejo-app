import { useMemo } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { AlertCircle, Mail } from 'lucide-react';
import { describeError } from '@/api/describeError';
import { GlassCard } from '@/components/GlassCard';
import { Notice } from '@/components/Notice';
import { StickyActionBar } from '@/components/StickyActionBar';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { NativeSelect } from '@/components/ui/native-select';
import { formatDate } from '@/lib/billing';
import { formatDecimal } from '@/lib/formatNumber';
import { MAX_INTERVAL_MINUTES, MIN_INTERVAL_MINUTES, NAME_MAX_LENGTH, buildWaveSchema, defaultFormValues, toCreatePayload } from '../lib/invitationWaveForm';
import { windowLabel } from '../lib/invitationWaveMath';

const BATCH_FIELD_ERRORS = ['INVITATION_WAVE_IMPORT_BATCH_NOT_FOUND', 'INVITATION_WAVE_TOO_MANY_RECIPIENTS'];
const INPUT_CLASS = 'h-11 md:h-8';
const FORM_ID = 'invitation-wave-form';

function FieldError({ id, error }) {
  if (!error) return null;
  return (
    <p id={id} role="alert" className="flex items-start gap-1.5 text-size-12-5 font-semibold text-danger">
      <AlertCircle className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
      <span>{error.message}</span>
    </p>
  );
}

function Field({ id, label, hint, error, children }) {
  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <Label htmlFor={id} className="text-size-13-5 font-semibold">
        {label}
        <span className="text-danger" aria-hidden="true">*</span>
      </Label>
      {children}
      {hint && (
        <span id={`${id}-hint`} className="text-size-12-5 text-ink-faint">
          {hint}
        </span>
      )}
      <FieldError id={`${id}-error`} error={error} />
    </div>
  );
}

function describedBy(id, error, hasHint = true) {
  return [hasHint ? `${id}-hint` : null, error ? `${id}-error` : null].filter(Boolean).join(' ') || undefined;
}

function batchOptionLabel(batch) {
  const people = `${formatDecimal(batch.studentsPendingActivation)} ${batch.studentsPendingActivation === 1 ? 'persona' : 'personas'}`;
  return `${batch.name} · ${people} · ${formatDate(batch.committedAt)}`;
}

function countIssues(schema, values) {
  const result = schema.safeParse(values);
  if (result.success) return 0;
  return new Set(result.error.issues.map((issue) => issue.path[0])).size;
}

export function WaveForm({ settings, batches, pending, onSubmit, onCancel }) {
  const schema = useMemo(() => buildWaveSchema(settings, batches), [settings, batches]);
  const form = useForm({
    resolver: zodResolver(schema),
    defaultValues: defaultFormValues(settings),
    mode: 'onTouched',
    reValidateMode: 'onChange',
  });
  const { register, handleSubmit, setError, control, formState } = form;
  const { errors } = formState;
  const values = useWatch({ control });
  const issueCount = countIssues(schema, values);
  const open = windowLabel(settings.sendWindow);

  async function submit(formValues) {
    try {
      await onSubmit(toCreatePayload(formValues));
    } catch (err) {
      if (BATCH_FIELD_ERRORS.includes(err?.code)) setError('importBatchId', { type: 'server', message: describeError(err).message });
    }
  }

  const barSummary = pending ? 'Creando el borrador…' : issueCount > 0 ? `${issueCount} ${issueCount === 1 ? 'campo para corregir' : 'campos para corregir'}` : 'Todo listo para crear';

  return (
    <>
      <GlassCard className="p-4 sm:p-5">
        <form id={FORM_ID} noValidate onSubmit={handleSubmit(submit)} className="flex flex-col gap-4" aria-busy={pending}>
          <Notice tone="accent">
            <span className="flex items-start gap-2">
              <Mail className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
              <span>
                <b>Invitamos con un mail de activación.</b> Cada persona elige su propia contraseña. Nunca enviamos contraseñas.
              </span>
            </span>
          </Notice>

          <Field id="wave-name" label="Nombre" hint="Para reconocerla después, por ejemplo «Ingresantes 2027»." error={errors.name}>
            <Input
              id="wave-name"
              className={INPUT_CLASS}
              maxLength={NAME_MAX_LENGTH}
              autoComplete="off"
              disabled={pending}
              aria-invalid={errors.name ? true : undefined}
              aria-describedby={describedBy('wave-name', errors.name)}
              {...register('name')}
            />
          </Field>

          <Field
            id="wave-batch"
            label="Lote de importación"
            hint="Solo aparecen lotes ya importados. Se invita a las personas de ese lote que todavía no activaron su cuenta."
            error={errors.importBatchId}
          >
            <NativeSelect
              id="wave-batch"
              className="[&_select]:h-11 md:[&_select]:h-8"
              disabled={pending}
              aria-invalid={errors.importBatchId ? true : undefined}
              aria-describedby={describedBy('wave-batch', errors.importBatchId)}
              {...register('importBatchId')}
            >
              <option value="">Elegí un lote…</option>
              {batches.map((batch) => (
                <option key={batch.id} value={String(batch.id)}>
                  {batchOptionLabel(batch)}
                </option>
              ))}
            </NativeSelect>
          </Field>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <Field id="wave-batch-size" label="Tamaño de tanda" hint="Mails por tanda." error={errors.batchSize}>
              <Input
                id="wave-batch-size"
                type="number"
                inputMode="numeric"
                min={1}
                className={INPUT_CLASS}
                disabled={pending}
                aria-invalid={errors.batchSize ? true : undefined}
                aria-describedby={describedBy('wave-batch-size', errors.batchSize)}
                {...register('batchSize', { deps: ['dailyCap'] })}
              />
            </Field>
            <Field id="wave-interval" label="Ritmo (minutos)" hint={`Entre tandas, de ${MIN_INTERVAL_MINUTES} a ${formatDecimal(MAX_INTERVAL_MINUTES)}.`} error={errors.intervalMinutes}>
              <Input
                id="wave-interval"
                type="number"
                inputMode="numeric"
                min={MIN_INTERVAL_MINUTES}
                max={MAX_INTERVAL_MINUTES}
                className={INPUT_CLASS}
                disabled={pending}
                aria-invalid={errors.intervalMinutes ? true : undefined}
                aria-describedby={describedBy('wave-interval', errors.intervalMinutes)}
                {...register('intervalMinutes')}
              />
            </Field>
            <Field id="wave-daily-cap" label="Tope diario" hint={`Máximo del instituto: ${formatDecimal(settings.globalDailyCap)} por día.`} error={errors.dailyCap}>
              <Input
                id="wave-daily-cap"
                type="number"
                inputMode="numeric"
                min={1}
                max={settings.globalDailyCap}
                className={INPUT_CLASS}
                disabled={pending}
                aria-invalid={errors.dailyCap ? true : undefined}
                aria-describedby={describedBy('wave-daily-cap', errors.dailyCap)}
                {...register('dailyCap', { deps: ['batchSize'] })}
              />
            </Field>
          </div>

          <p className="rounded-xl bg-neutral-bg px-3 py-2.5 text-size-13 text-ink" aria-live="polite">
            Se enviarán hasta {Number(values.batchSize) || 0} mails cada {Number(values.intervalMinutes) || 0} minutos, con un máximo de {Number(values.dailyCap) || 0} por día.
            {open && ` Los mails salen entre las ${open.replace(' a ', ' y las ')}.`}
          </p>

          <div className="hidden justify-end gap-2 md:flex">
            <Button type="button" variant="outline" className="md:min-h-8" disabled={pending} onClick={onCancel}>
              Cancelar
            </Button>
            <Button type="submit" variant="primary" className="md:min-h-8" loading={pending}>
              Crear borrador
            </Button>
          </div>
        </form>
      </GlassCard>

      <StickyActionBar className="md:hidden" role="region" aria-label="Acciones de la pantalla" summary={barSummary}>
        <Button type="button" variant="outline" className="min-h-11" disabled={pending} onClick={onCancel}>
          Cancelar
        </Button>
        <Button type="submit" form={FORM_ID} variant="primary" className="min-h-11" loading={pending}>
          Crear borrador
        </Button>
      </StickyActionBar>
    </>
  );
}
