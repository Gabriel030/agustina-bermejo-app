import { useEffect, useRef, useState } from 'react';
import { AlertCircle, Check } from 'lucide-react';
import { toast } from 'sonner';
import { ChangesTable } from '@/components/ChangesTable';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { PageContainer } from '@/components/PageContainer';
import { Notice } from '@/components/Notice';
import { Skeleton } from '@/components/Skeleton';
import { StickyActionBar } from '@/components/StickyActionBar';
import { Stepper } from '@/components/Stepper';
import { StatusPill } from '@/components/StatusPill';
import { InfoTip } from '@/components/charts/InfoTip';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Switch } from '@/components/ui/switch';
import { useUnsavedChangesGuard } from '@/features/academic-structure';
import { useInstitutionSettings, useSaveInstitutionSettings } from '../hooks/useInstitutionSettings';
import { SETTING_FIELDS, SETTING_GROUPS, buildChangeRows, isChanged, toDraft, toPayload, validateField } from '../institutionSettings';

const STEPS = [
  { key: 'changes', label: 'Cambios' },
  { key: 'confirm', label: 'Confirmar' },
];

function SettingRow({ field, saved, value, error, changed, onChange, onBlur }) {
  const Icon = field.icon;
  const inputId = `setting-${field.key}`;
  const hintId = `${inputId}-hint`;
  const errorId = `${inputId}-error`;
  return (
    <div className="flex flex-col gap-3 py-4 sm:flex-row sm:items-start sm:justify-between">
      <div className="flex min-w-0 items-start gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-accent-soft text-primary">
          <Icon className="size-5" aria-hidden="true" />
        </span>
        <div className="flex min-w-0 flex-wrap items-center gap-2 pt-2">
          <label htmlFor={inputId} id={`${inputId}-label`} className="font-medium text-ink">
            {field.label}
          </label>
          <InfoTip label={`Más información sobre ${field.label.toLowerCase()}`}>{field.tip}</InfoTip>
          {changed && (
            <StatusPill tone="accent">
              <Check className="size-3" aria-hidden="true" />
              Modificado
            </StatusPill>
          )}
        </div>
      </div>
      <div className="flex flex-col gap-1 pl-[3.25rem] sm:items-end sm:pl-0">
        {field.boolean ? (
          <>
            <label htmlFor={inputId} className="flex min-h-11 cursor-pointer items-center gap-3">
              <Switch id={inputId} checked={value} onCheckedChange={onChange} aria-labelledby={`${inputId}-label`} aria-describedby={hintId} />
              <span className="min-w-24 text-sm font-semibold text-ink" aria-hidden="true">
                {value ? 'Activado' : 'Desactivado'}
              </span>
            </label>
            <span id={hintId} className="text-xs text-ink-faint">
              Hoy: {saved ? 'activado' : 'desactivado'}
            </span>
          </>
        ) : (
          <>
            <div className="flex items-center gap-2">
              <Input
                id={inputId}
                type="text"
                inputMode="numeric"
                autoComplete="off"
                className="h-11 w-24 text-right tabular-nums"
                value={value}
                aria-invalid={error ? 'true' : 'false'}
                aria-describedby={error ? `${hintId} ${errorId}` : hintId}
                onChange={(event) => onChange(event.target.value)}
                onBlur={onBlur}
              />
              <span className="text-sm text-ink-soft">{field.unit}</span>
            </div>
            <span id={hintId} className="text-xs tabular-nums text-ink-faint">
              Entre {field.min} y {field.max} · Hoy: {saved}
            </span>
            {error && (
              <span id={errorId} role="alert" className="flex items-center gap-1 text-xs font-medium text-danger">
                <AlertCircle className="size-3.5 shrink-0" aria-hidden="true" />
                {error}
              </span>
            )}
          </>
        )}
      </div>
    </div>
  );
}

function describeBlockReason(invalidCount, changedCount) {
  if (invalidCount > 0) return `Corregí ${invalidCount} ${invalidCount === 1 ? 'campo' : 'campos'} para poder guardar`;
  if (changedCount === 0) return 'Cambiá un valor para habilitar Guardar';
  return 'Vas a revisar qué cambia antes de guardar';
}

function compactBlockReason(invalidCount, changedCount) {
  if (invalidCount > 0) return `Corregí ${invalidCount} ${invalidCount === 1 ? 'campo' : 'campos'}`;
  if (changedCount === 0) return 'Cambiá un valor';
  return 'Se revisa antes';
}

function ChangesBar({ count, invalidCount, onDiscard, onReview }) {
  const blocked = count === 0 || invalidCount > 0;
  return (
    <StickyActionBar
      compact="menu"
      aria-label="Cambios sin guardar"
      compactSummary={count === 0 ? 'Sin cambios' : `${count} ${count === 1 ? 'cambio' : 'cambios'}`}
      compactHint={compactBlockReason(invalidCount, count)}
      primary={{
        label: 'Guardar cambios',
        onClick: onReview,
        className: 'px-5 aria-disabled:cursor-not-allowed aria-disabled:opacity-50',
        'aria-disabled': blocked ? 'true' : undefined,
      }}
      secondary={[{ key: 'discard', label: 'Descartar', destructive: true, onSelect: onDiscard, className: 'px-4 aria-disabled:cursor-not-allowed aria-disabled:opacity-50', 'aria-disabled': count === 0 ? 'true' : undefined }]}
      summary={
        <span aria-live="polite" aria-atomic="true">
        {count === 0 ? (
          <span className="text-sm font-semibold text-ink-faint">Sin cambios por guardar</span>
        ) : (
          <StatusPill tone="accent" className="text-size-13">
            <AlertCircle className="size-3.5" aria-hidden="true" />
            {count} {count === 1 ? 'cambio sin guardar' : 'cambios sin guardar'}
          </StatusPill>
        )}
        </span>
      }
      hint={
        <span aria-live="polite" className={invalidCount > 0 ? 'font-semibold text-danger' : undefined}>
          {describeBlockReason(invalidCount, count)}
        </span>
      }
    />
  );
}

function LoadingSettings() {
  return (
    <GlassCard className="flex flex-col gap-5 p-5" aria-busy="true" aria-label="Cargando ajustes">
      {[0, 1, 2].map((item) => (
        <div key={item} className="flex items-center gap-3">
          <Skeleton className="size-10" />
          <Skeleton className="h-5 flex-1" />
          <Skeleton className="h-10 w-28" />
        </div>
      ))}
    </GlassCard>
  );
}

function SettingsEditor({ settings }) {
  const save = useSaveInstitutionSettings();
  const [edits, setEdits] = useState({});
  const [touched, setTouched] = useState({});
  const [step, setStep] = useState('changes');
  const [saveError, setSaveError] = useState(null);
  const confirmHeadingRef = useRef(null);
  const refocusFirstField = useRef(false);

  const draft = { ...toDraft(settings), ...edits };
  const changedCount = SETTING_FIELDS.filter((field) => isChanged(field, settings, draft)).length;
  const errors = Object.fromEntries(SETTING_FIELDS.map((field) => [field.key, validateField(field, draft[field.key])]));
  const invalidFields = SETTING_FIELDS.filter((field) => errors[field.key]);
  const { guardDialog } = useUnsavedChangesGuard(
    changedCount > 0,
    `Tenés ${changedCount} ${changedCount === 1 ? 'cambio sin guardar' : 'cambios sin guardar'} en los ajustes. Si salís ahora, se pierden.`,
  );

  useEffect(() => {
    if (step === 'confirm') confirmHeadingRef.current?.focus();
    if (step === 'changes' && refocusFirstField.current) {
      refocusFirstField.current = false;
      document.getElementById(`setting-${SETTING_FIELDS[0].key}`)?.focus();
    }
  }, [step]);

  function edit(field, value) {
    setEdits((current) => ({ ...current, [field.key]: value }));
    setTouched((current) => ({ ...current, [field.key]: true }));
  }

  function discard() {
    if (changedCount === 0) return;
    setEdits({});
    setTouched({});
    toast.success('Cambios descartados');
  }

  function review() {
    if (invalidFields.length > 0) {
      setTouched(Object.fromEntries(SETTING_FIELDS.map((field) => [field.key, true])));
      document.getElementById(`setting-${invalidFields[0].key}`)?.focus();
      return;
    }
    if (changedCount === 0) return;
    setSaveError(null);
    setStep('confirm');
  }

  function backToChanges() {
    refocusFirstField.current = true;
    setSaveError(null);
    setStep('changes');
  }

  async function confirm() {
    setSaveError(null);
    try {
      await save.mutateAsync(toPayload(draft));
      setEdits({});
      setTouched({});
      refocusFirstField.current = true;
      setStep('changes');
      toast.success('Ajustes guardados');
    } catch (error) {
      const message = error.message || 'No pudimos guardar los ajustes. Probá de nuevo.';
      setSaveError(message);
    }
  }

  const stepper = (
    <Stepper
      pasos={STEPS}
      pasoActual={step}
      pasosVisitados={step === 'confirm' ? ['changes', 'confirm'] : ['changes']}
      onStepClick={(key) => key === 'changes' && !save.isPending && backToChanges()}
    />
  );

  if (step === 'confirm') {
    const rows = buildChangeRows(settings, draft);
    return (
      <>
        {stepper}
        {saveError && (
          <Notice tone="danger" role="alert" className="flex items-start gap-2 px-4 py-3 text-sm">
            <AlertCircle className="mt-0.5 size-4 shrink-0 text-danger" aria-hidden="true" />
            <span>{saveError} Tus cambios siguen acá.</span>
          </Notice>
        )}
        <GlassCard className="flex flex-col gap-4 p-5">
          <h2 ref={confirmHeadingRef} tabIndex={-1} className="font-heading text-lg font-semibold text-ink outline-none">
            Qué cambia
          </h2>
          <ChangesTable rows={rows} />
        </GlassCard>
        <StickyActionBar
          summary={`${changedCount} ${changedCount === 1 ? 'ajuste cambia' : 'ajustes cambian'}`}
          hint="Se aplica a toda la institución al guardar."
        >
          <Button variant="outline" className="h-11 px-4" onClick={backToChanges} disabled={save.isPending}>
            Volver
          </Button>
          <Button variant="primary" className="h-11 px-5" onClick={confirm} loading={save.isPending}>
            Confirmar y guardar
          </Button>
        </StickyActionBar>
        {guardDialog}
      </>
    );
  }

  return (
    <>
      {stepper}
      <GlassCard className="flex flex-col divide-y divide-border px-5 py-2">
        {SETTING_GROUPS.map((group) => (
          <section key={group.key} aria-labelledby={`group-${group.key}`} className="py-2 first:pt-0 last:pb-0">
            <h2 id={`group-${group.key}`} className="pt-3 text-xs font-bold uppercase tracking-wide text-ink-faint">
              {group.label}
            </h2>
            <div className="divide-y divide-border">
              {SETTING_FIELDS.filter((field) => field.group === group.key).map((field) => (
                <SettingRow
                  key={field.key}
                  field={field}
                  saved={settings[field.key]}
                  value={draft[field.key]}
                  error={touched[field.key] ? errors[field.key] : null}
                  changed={isChanged(field, settings, draft)}
                  onChange={(value) => edit(field, value)}
                  onBlur={() => setTouched((current) => ({ ...current, [field.key]: true }))}
                />
              ))}
            </div>
          </section>
        ))}
      </GlassCard>
      <ChangesBar count={changedCount} invalidCount={invalidFields.length} onDiscard={discard} onReview={review} />
      {guardDialog}
    </>
  );
}

export function DirectorInstitutionSettings() {
  const query = useInstitutionSettings();

  return (
    <PageContainer title="Ajustes del instituto">
      {query.isPending && <LoadingSettings />}
      {query.isError && (
        <LoadErrorState error={query.error} onRetry={() => query.refetch()} resource="los ajustes" distinguishForbidden backTo="/director/inicio" backLabel="el inicio" />
      )}
      {query.data && <SettingsEditor settings={query.data} />}
    </PageContainer>
  );
}
