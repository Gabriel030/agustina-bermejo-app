import { useEffect, useMemo, useState } from 'react';
import { InfoTip } from '@/components/charts/InfoTip';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { formatDate } from '@/lib/billing';
import { ViolationList } from '@/features/billing/discounts/ViolationList';
import {
  useReminderImpact,
  useReminderSettingsValidation,
  useReminderValidation,
  useSubmitReminderChange,
  useSubmitReminderRemoval,
  useSubmitReminderSettings,
} from '@/features/billing/hooks/useCollectionReminders';
import {
  SETTINGS_KEYS,
  describeNotice,
  diffSchedules,
  diffSettingValues,
  formatSettingValue,
  localViolations,
  tomorrowIso,
} from './reminderSchedule';

const KIND_LABELS = { added: 'Aviso nuevo', edited: 'Aviso editado', removed: 'Aviso eliminado', setting: 'Ajuste propio', changed: 'Cambia' };

function buildConfirmRows(kind, base, draft) {
  if (kind === 'removal') {
    return [{ key: 'exception', label: 'Excepción', kind: 'changed', today: 'Con avisos propios', next: 'Vuelve a la política del grupo' }];
  }
  if (kind === 'settings') {
    return diffSettingValues(base, draft, SETTINGS_KEYS).map((row) => ({
      ...row,
      kind: 'changed',
      today: formatSettingValue(row.key, row.today).replace('Hereda del general', '—'),
      next: formatSettingValue(row.key, row.next).replace('Hereda del general', '—'),
    }));
  }
  return diffSchedules(base, draft).map((row) => {
    if (row.kind === 'setting') {
      return { ...row, today: formatSettingValue(row.key, row.today), next: formatSettingValue(row.key, row.next) };
    }
    return { ...row, today: row.today ? describeNotice(row.today) : '—', next: row.next ? describeNotice(row.next) : '—' };
  });
}

function ChangesTable({ rows }) {
  return (
    <div className="flex flex-col gap-3">
      <div className="hidden grid-cols-[1.2fr_1fr_1fr_0.8fr] gap-3 text-xs font-semibold uppercase tracking-wide text-ink-faint sm:grid" aria-hidden="true">
        <span />
        <span>Hoy</span>
        <span>Nuevo</span>
        <span>Diferencia</span>
      </div>
      {rows.map((row) => (
        <div key={`${row.kind}-${row.key}`} role="group" aria-label={row.label} className="grid gap-1 border-t border-border pt-3 first:border-t-0 first:pt-0 sm:grid-cols-[1.2fr_1fr_1fr_0.8fr] sm:gap-3">
          <span className="font-medium text-ink">{row.label}</span>
          <span className="text-sm text-ink-soft">
            <span className="mr-2 text-xs text-ink-faint sm:hidden">Hoy</span>
            {row.today ?? '—'}
          </span>
          <span className="text-sm text-ink">
            <span className="mr-2 text-xs text-ink-faint sm:hidden">Nuevo</span>
            {row.next ?? '—'}
          </span>
          <span className="text-sm font-semibold text-ink">{KIND_LABELS[row.kind]}</span>
        </div>
      ))}
    </div>
  );
}

function ImpactBlock({ kind, impact }) {
  if (impact.isPending) return <Skeleton className="h-16 w-full" aria-label="Calculando impacto" />;
  if (impact.isError) return <p className="text-sm text-ink-soft">No pudimos calcular el impacto. Podés enviar el cambio igual.</p>;
  if (!impact.data) return null;
  const count = impact.data.studentsWithChangedSchedule;
  if (count === 0 && kind === 'settings') {
    return (
      <p className="text-sm text-ink-soft">
        Este cambio no mueve a ningún alumno de calendario: ajusta cómo y cuándo se envían los avisos desde la fecha de vigencia.
      </p>
    );
  }
  return (
    <div className="flex items-baseline gap-3">
      <span className="font-heading text-4xl font-bold tabular-nums text-ink">{count}</span>
      <span className="flex items-center gap-1 text-sm text-ink-soft">
        Alumnos afectados
        <InfoTip>Alumnos con cuotas abiertas que pasan a recibir un calendario de avisos distinto.</InfoTip>
      </span>
    </div>
  );
}

export function ReminderConfirmStep({ kind, scope, base, draft, isDirector, onBack, onDone }) {
  const [effectiveFrom, setEffectiveFrom] = useState(tomorrowIso());
  const [reason, setReason] = useState('');
  const [serverViolations, setServerViolations] = useState([]);
  const impact = useReminderImpact();
  const scheduleValidation = useReminderValidation();
  const settingsValidation = useReminderSettingsValidation();
  const submitSchedule = useSubmitReminderChange();
  const submitRemoval = useSubmitReminderRemoval();
  const submitSettings = useSubmitReminderSettings();

  const rows = useMemo(() => buildConfirmRows(kind, base, draft), [kind, base, draft]);
  const validDate = effectiveFrom >= tomorrowIso();
  const local = kind === 'schedule' ? localViolations(draft) : [];

  const scheduleBody = useMemo(
    () => (kind === 'schedule' ? { followsGroupSchedule: false, notices: draft.notices, sendingOverrides: draft.sendingOverrides } : null),
    [kind, draft],
  );
  const changeReason = reason.trim();
  const payload =
    kind === 'settings'
      ? { changeReason, effectiveFrom, values: draft }
      : kind === 'removal'
        ? { changeReason, effectiveFrom }
        : { changeReason, effectiveFrom, schedule: scheduleBody };

  const { mutate: runImpact } = impact;
  const { mutate: validateSchedule } = scheduleValidation;
  const { mutate: validateSettings } = settingsValidation;

  useEffect(() => {
    if (!validDate || kind === 'removal') return;
    if (kind === 'settings') {
      runImpact({ effectiveFrom, globalSettings: draft });
    } else {
      runImpact({ group: scope.group, targetId: scope.targetId ?? undefined, schedule: scheduleBody, effectiveFrom });
    }
  }, [kind, validDate, effectiveFrom, draft, scope, scheduleBody, runImpact]);

  useEffect(() => {
    if (!validDate) return;
    if (kind === 'settings') validateSettings({ changeReason: 'Validación', effectiveFrom, values: draft });
    else if (kind === 'schedule') validateSchedule({ scope, payload: { changeReason: 'Validación', effectiveFrom, schedule: scheduleBody } });
  }, [kind, validDate, effectiveFrom, draft, scope, scheduleBody, validateSchedule, validateSettings]);

  const liveViolations = (kind === 'settings' ? settingsValidation : scheduleValidation).data?.violations ?? [];
  const violations = [...local.map((message) => ({ message })), ...liveViolations, ...serverViolations];
  const submitting = submitSchedule.isPending || submitRemoval.isPending || submitSettings.isPending;
  const canSubmit = validDate && reason.trim() !== '' && violations.length === 0 && rows.length > 0;

  async function submit() {
    setServerViolations([]);
    try {
      const result =
        kind === 'settings'
          ? await submitSettings.mutateAsync(payload)
          : kind === 'removal'
            ? await submitRemoval.mutateAsync({ scope, payload })
            : await submitSchedule.mutateAsync({ scope, payload });
      onDone({ pending: result?.authorizationRequestId != null, requestId: result?.authorizationRequestId ?? null });
    } catch (error) {
      setServerViolations(error.violations?.length ? error.violations : [{ message: error.message }]);
    }
  }

  return (
    <div className="flex flex-col gap-5">
      <GlassCard className="flex flex-col gap-4 p-5">
        <h2 className="font-heading text-lg font-semibold text-ink">Qué cambia</h2>
        {rows.length === 0 ? <p className="text-sm text-ink-soft">No hay cambios para confirmar.</p> : <ChangesTable rows={rows} />}
      </GlassCard>
      <GlassCard className="flex flex-col gap-4 p-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="effective-from">Vigencia</Label>
            <Input id="effective-from" type="date" min={tomorrowIso()} value={effectiveFrom} onChange={(event) => setEffectiveFrom(event.target.value)} />
            <span className="text-xs text-ink-faint">{validDate ? `Rige desde el ${formatDate(effectiveFrom)}` : 'La vigencia empieza como mínimo mañana.'}</span>
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="change-reason">Motivo (obligatorio)</Label>
            <Textarea id="change-reason" value={reason} onChange={(event) => setReason(event.target.value)} />
          </div>
        </div>
        {kind !== 'removal' && <ImpactBlock kind={kind} impact={impact} />}
        <ViolationList violations={violations} title="No pudimos guardar el cambio" />
      </GlassCard>
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="outline" onClick={onBack} disabled={submitting}>
          Volver a los cambios
        </Button>
        <Button variant="gradient" onClick={submit} disabled={!canSubmit} loading={submitting}>
          {isDirector ? 'Publicar cambio' : 'Enviar propuesta'}
        </Button>
      </div>
    </div>
  );
}
