import { useState } from 'react';
import { toast } from 'sonner';
import { ChangesTable } from '@/components/ChangesTable';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { Stepper } from '@/components/Stepper';
import { StatusPill } from '@/components/StatusPill';
import { InfoTip } from '@/components/charts/InfoTip';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Switch } from '@/components/ui/switch';
import { useInstitutionSettings, useSaveInstitutionSettings } from '../hooks/useInstitutionSettings';
import { SETTING_FIELDS, SETTING_GROUPS, buildChangeRows, isChanged, toDraft, toPayload, validateField } from '../institutionSettings';

const STEPS = [
  { key: 'changes', label: 'Cambios' },
  { key: 'confirm', label: 'Confirmar' },
];

function SettingRow({ field, value, error, changed, onChange, onBlur }) {
  const Icon = field.icon;
  const inputId = `setting-${field.key}`;
  return (
    <div className="flex flex-col gap-3 py-4 sm:flex-row sm:items-start sm:justify-between">
      <div className="flex min-w-0 items-start gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-accent-soft text-primary">
          <Icon className="size-5" aria-hidden="true" />
        </span>
        <div className="flex min-w-0 flex-wrap items-center gap-2 pt-2">
          <label htmlFor={inputId} className="font-medium text-ink">
            {field.label}
          </label>
          <InfoTip label={`Más información sobre ${field.label.toLowerCase()}`}>{field.tip}</InfoTip>
          {changed && <StatusPill tone="accent">Modificado</StatusPill>}
        </div>
      </div>
      <div className="flex flex-col gap-1 pl-[3.25rem] sm:items-end sm:pl-0">
        {field.boolean ? (
          <Switch id={inputId} checked={value} onCheckedChange={onChange} aria-label={field.label} />
        ) : (
          <>
            <div className="flex items-center gap-2">
              <Input
                id={inputId}
                type="text"
                inputMode="numeric"
                className="h-11 w-24 text-right"
                value={value}
                aria-invalid={error ? 'true' : 'false'}
                aria-describedby={`${inputId}-hint`}
                onChange={(event) => onChange(event.target.value)}
                onBlur={onBlur}
              />
              <span className="text-sm text-ink-soft">{field.unit}</span>
            </div>
            <span id={`${inputId}-hint`} className="text-xs text-ink-faint">
              Entre {field.min} y {field.max}
            </span>
            {error && (
              <span role="alert" className="text-xs font-medium text-danger">
                {error}
              </span>
            )}
          </>
        )}
      </div>
    </div>
  );
}

function ChangesBar({ count, invalid, onDiscard, onReview }) {
  return (
    <GlassCard className="sticky bottom-4 z-10 flex flex-wrap items-center justify-between gap-3 px-5 py-3">
      <span className="text-sm font-semibold text-ink" aria-live="polite">
        {count === 0 ? (
          <span className="font-normal text-ink-faint">Sin cambios por guardar</span>
        ) : (
          <StatusPill tone="accent">
            {count} {count === 1 ? 'cambio sin guardar' : 'cambios sin guardar'}
          </StatusPill>
        )}
      </span>
      <div className="flex gap-2">
        <Button variant="ghost" onClick={onDiscard} disabled={count === 0}>
          Descartar
        </Button>
        <Button variant="gradient" onClick={onReview} disabled={count === 0 || invalid}>
          Revisar cambios
        </Button>
      </div>
    </GlassCard>
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

  const draft = { ...toDraft(settings), ...edits };
  const changedCount = SETTING_FIELDS.filter((field) => isChanged(field, settings, draft)).length;
  const errors = Object.fromEntries(SETTING_FIELDS.map((field) => [field.key, validateField(field, draft[field.key])]));
  const invalid = Object.values(errors).some(Boolean);

  function edit(field, value) {
    setEdits((current) => ({ ...current, [field.key]: value }));
    setTouched((current) => ({ ...current, [field.key]: true }));
  }

  function discard() {
    setEdits({});
    setTouched({});
  }

  async function confirm() {
    try {
      await save.mutateAsync(toPayload(draft));
      setEdits({});
      setTouched({});
      setStep('changes');
      toast.success('Ajustes guardados');
    } catch (error) {
      toast.error(error.message || 'No pudimos guardar los ajustes. Probá de nuevo.');
    }
  }

  if (step === 'confirm') {
    const rows = buildChangeRows(settings, draft);
    return (
      <>
        <Stepper pasos={STEPS} pasoActual="confirm" pasosVisitados={['changes', 'confirm']} onStepClick={(key) => key === 'changes' && setStep('changes')} />
        <GlassCard className="flex flex-col gap-4 p-5">
          <h2 className="font-heading text-lg font-semibold text-ink">Qué cambia</h2>
          <ChangesTable rows={rows} />
        </GlassCard>
        <div className="flex flex-wrap items-center justify-end gap-2">
          <span className="mr-auto text-sm font-semibold text-ink">
            {changedCount} {changedCount === 1 ? 'ajuste cambia' : 'ajustes cambian'}
          </span>
          <Button variant="outline" onClick={() => setStep('changes')} disabled={save.isPending}>
            Volver
          </Button>
          <Button variant="gradient" onClick={confirm} loading={save.isPending}>
            Confirmar y guardar
          </Button>
        </div>
      </>
    );
  }

  return (
    <>
      {changedCount > 0 && <Stepper pasos={STEPS} pasoActual="changes" pasosVisitados={['changes']} />}
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
      <ChangesBar count={changedCount} invalid={invalid} onDiscard={discard} onReview={() => setStep('confirm')} />
    </>
  );
}

export function DirectorInstitutionSettings() {
  const query = useInstitutionSettings();

  return (
    <PageContainer title="Ajustes del instituto">
      {query.isPending && <LoadingSettings />}
      {query.isError && <LoadErrorState error={query.error} onRetry={() => query.refetch()} resource="los ajustes" />}
      {query.data && <SettingsEditor settings={query.data} />}
    </PageContainer>
  );
}
